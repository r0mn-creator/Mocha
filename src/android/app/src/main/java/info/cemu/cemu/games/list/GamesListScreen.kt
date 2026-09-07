@file:OptIn(
    ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class,
)

package info.cemu.cemu.games.list

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.provider.DocumentsContract
import android.view.KeyEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import info.cemu.cemu.R
import info.cemu.cemu.common.input.GamepadInputSource
import info.cemu.cemu.common.settings.AppSettingsStore
import info.cemu.cemu.common.settings.GameListViewMode
import info.cemu.cemu.common.ui.extensions.showMessage
import info.cemu.cemu.common.ui.localization.tr
import info.cemu.cemu.games.GameIcon
import info.cemu.cemu.games.boxart.BoxArtImage
import info.cemu.cemu.nativeinterface.NativeActiveSettings
import info.cemu.cemu.nativeinterface.NativeGameTitles
import info.cemu.cemu.nativeinterface.NativeGameTitles.Game
import info.cemu.cemu.provider.DocumentsProvider
import info.cemu.cemu.settings.gamespath.GamesPathsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import androidx.compose.material3.DropdownMenuItem as MaterialDropdownMenuItem

@Composable
fun GamesListScreen(
    gamesListViewModel: GamesListViewModel = viewModel(),
    goToGameDetails: (Game) -> Unit,
    goToGameEditProfile: (Game) -> Unit,
    startGame: (Game) -> Unit,
    goToSettings: () -> Unit,
    goToTitleManager: () -> Unit,
    goToGraphicPacks: () -> Unit,
    goToAboutCemu: () -> Unit,
    tryCreateShortcut: (Game) -> Boolean,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val games by gamesListViewModel.games.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val gameListViewMode by AppSettingsStore.dataStore.data
        .map { it.guiSettings.gameListViewMode }
        .collectAsStateWithLifecycle(initialValue = GameListViewMode.LIST)

    val state = rememberPullToRefreshState()

    // Games/Graphic Packs/Title Manager/Settings, in top-bar left-to-right
    // order - this is also the order L1/R1 cycle through. Index 0 is this
    // screen itself, so navigating "to" it is a no-op.
    val topBarTabs = listOf(
        tr("Games") to {},
        tr("Graphic packs") to goToGraphicPacks,
        tr("Title manager") to goToTitleManager,
        tr("Settings") to goToSettings,
    )

    LaunchedEffect(Unit) {
        GamepadInputSource.keyEvents.collect { (keyEvent) ->
            if (keyEvent.action != KeyEvent.ACTION_DOWN) return@collect
            val delta = when (keyEvent.keyCode) {
                KeyEvent.KEYCODE_BUTTON_L1 -> -1
                KeyEvent.KEYCODE_BUTTON_R1 -> 1
                else -> return@collect
            }
            // Currently-showing tab is always index 0 (this screen); the
            // other three are reached directly rather than tracked as a
            // separate highlight state, since there is nothing to highlight
            // towards until navigation actually happens.
            val target = ((0 + delta) + topBarTabs.size) % topBarTabs.size
            topBarTabs[target].second()
        }
    }

    LaunchedEffect(lifecycleState) {
        if (lifecycleState == Lifecycle.State.RESUMED && gamesListViewModel.gamePathsHaveChanged())
            gamesListViewModel.refreshGames()
    }

    val gamesPathsViewModel: GamesPathsViewModel = viewModel()
    val gamesPaths by gamesPathsViewModel.gamesPaths.collectAsState()
    val addGamesPathLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            val documentFile =
                DocumentFile.fromTreeUri(context, uri) ?: return@rememberLauncherForActivityResult
            val gamesPath = documentFile.uri.toString()
            if (gamesPaths.contains(gamesPath)) {
                snackbarHostState.showMessage(coroutineScope, tr("Games path already added"))
                return@rememberLauncherForActivityResult
            }
            gamesPathsViewModel.addGamesPath(gamesPath)
        }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            gamesListViewModel.setFilterText("")
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { addGamesPathLauncher.launch(null) }) {
                Icon(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = tr("Add games directory"),
                )
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    GamesTopBarTabs(tabs = topBarTabs, currentIndex = 0)
                },
                actions = {
                    GameListToolBarActionsMenu(
                        goToAboutCemu = goToAboutCemu,
                        openCemuFolder = {
                            if (!tryOpenCemuFolder(context)) {
                                snackbarHostState.showMessage(
                                    coroutineScope,
                                    tr("Failed to open Cemu folder")
                                )
                            }
                        },
                        shareLogFile = {
                            if (!logFileExists()) {
                                snackbarHostState.showMessage(
                                    coroutineScope,
                                    tr("Log file doesn't exist")
                                )
                                return@GameListToolBarActionsMenu
                            }

                            if (!tryShareLogFile(context)) {
                                snackbarHostState.showMessage(
                                    coroutineScope,
                                    tr("Failed to open log file")
                                )
                            }
                        },
                    )
                },
            )
        },
    ) { scaffoldPadding ->
        Box(
            modifier = Modifier
                .padding(scaffoldPadding)
                .fillMaxSize()
                .pullToRefresh(
                    isRefreshing = refreshing,
                    state = state,
                    onRefresh = {
                        coroutineScope.launch {
                            refreshing = true
                            gamesListViewModel.refreshGames()
                            delay(1500)
                            refreshing = false
                        }
                    },
                ),
        ) {
            val deleteShaderCaches: (Game) -> Unit = {
                gamesListViewModel.removeShadersForGame(it)
                snackbarHostState.showMessage(coroutineScope, tr("Shader caches removed"))
            }
            val createShortcut: (Game) -> Unit = {
                if (!tryCreateShortcut(it)) {
                    snackbarHostState.showMessage(
                        coroutineScope,
                        tr("Couldn't create shortcut for game")
                    )
                }
            }

            when (gameListViewMode) {
                GameListViewMode.LIST -> GameList(
                    games = games,
                    setFavorite = gamesListViewModel::setGameTitleFavorite,
                    deleteShaderCaches = deleteShaderCaches,
                    startGame = startGame,
                    goToGameDetails = goToGameDetails,
                    goToGameEditProfile = goToGameEditProfile,
                    createShortcut = createShortcut,
                )

                GameListViewMode.GRID -> GameGridList(
                    games = games,
                    columns = gridColumnsForOrientation(landscape = 6, portrait = 4),
                    imageAspectRatio = 1f,
                    imageContent = { game, modifier -> GameIcon(game = game, modifier = modifier) },
                    setFavorite = gamesListViewModel::setGameTitleFavorite,
                    deleteShaderCaches = deleteShaderCaches,
                    startGame = startGame,
                    goToGameDetails = goToGameDetails,
                    goToGameEditProfile = goToGameEditProfile,
                    createShortcut = createShortcut,
                )

                GameListViewMode.BOX_ART -> GameGridList(
                    games = games,
                    columns = gridColumnsForOrientation(landscape = 5, portrait = 3),
                    imageAspectRatio = 457f / 640f,
                    imageContent = { game, modifier -> BoxArtImage(game = game, modifier = modifier) },
                    setFavorite = gamesListViewModel::setGameTitleFavorite,
                    deleteShaderCaches = deleteShaderCaches,
                    startGame = startGame,
                    goToGameDetails = goToGameDetails,
                    goToGameEditProfile = goToGameEditProfile,
                    createShortcut = createShortcut,
                )
            }

            PullToRefreshDefaults.Indicator(
                modifier = Modifier.align(Alignment.TopCenter),
                isRefreshing = refreshing,
                state = state,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GameList(
    games: List<Game>,
    startGame: (Game) -> Unit,
    goToGameDetails: (Game) -> Unit,
    goToGameEditProfile: (Game) -> Unit,
    setFavorite: (Game, Boolean) -> Unit,
    createShortcut: (Game) -> Unit,
    deleteShaderCaches: (Game) -> Unit,
) {
    LazyVerticalGrid(
        modifier = Modifier
            .padding(8.dp)
            .fillMaxSize(),
        columns = GridCells.Adaptive(620.dp),
    ) {
        items(items = games, key = { it.path }) { game ->
            var showDeleteShaderConfirmationDialog by remember { mutableStateOf(false) }
            GameListItem(
                modifier = Modifier.animateItem(),
                game = game,
                onStartGame = startGame,
                onIsFavoriteChanged = { isFavorite ->
                    setFavorite(game, isFavorite)
                },
                onEditGameProfile = {
                    goToGameEditProfile(game)
                },
                onRemoveShaderCaches = { showDeleteShaderConfirmationDialog = true },
                onAboutTitle = {
                    goToGameDetails(game)
                },
                onCreateShortcut = {
                    createShortcut(game)
                },
            )

            if (showDeleteShaderConfirmationDialog) {
                ShaderCachesConfirmationDialog(
                    gameName = game.name ?: "",
                    onDismissRequest = { showDeleteShaderConfirmationDialog = false },
                    onConfirm = {
                        deleteShaderCaches(game)
                        showDeleteShaderConfirmationDialog = false
                    },
                )
            }
        }
    }
}

/** Returns [landscape] or [portrait] depending on the current screen
 *  orientation - box art and icon grids each want a different column count
 *  per orientation, so this is called with different values per grid rather
 *  than hard-coding one fixed count. */
@Composable
private fun gridColumnsForOrientation(landscape: Int, portrait: Int): Int =
    if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) landscape else portrait

/** The grid counterpart to [GameList] - same callbacks, same context menu
 *  and shader-cache dialog, laid out as tiles instead of rows. Used for both
 *  the plain icon grid and the libretro box-art grid, which only differ in
 *  column count, tile aspect ratio, and how each tile's image is drawn. */
@Composable
private fun GameGridList(
    games: List<Game>,
    columns: Int,
    imageAspectRatio: Float,
    imageContent: @Composable (Game, Modifier) -> Unit,
    startGame: (Game) -> Unit,
    goToGameDetails: (Game) -> Unit,
    goToGameEditProfile: (Game) -> Unit,
    setFavorite: (Game, Boolean) -> Unit,
    createShortcut: (Game) -> Unit,
    deleteShaderCaches: (Game) -> Unit,
) {
    LazyVerticalGrid(
        modifier = Modifier
            .padding(8.dp)
            .fillMaxSize(),
        columns = GridCells.Fixed(columns),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items = games, key = { it.path }) { game ->
            var showDeleteShaderConfirmationDialog by remember { mutableStateOf(false) }
            GameGridItem(
                modifier = Modifier.animateItem(),
                game = game,
                imageAspectRatio = imageAspectRatio,
                imageContent = imageContent,
                onStartGame = startGame,
                onIsFavoriteChanged = { isFavorite -> setFavorite(game, isFavorite) },
                onEditGameProfile = { goToGameEditProfile(game) },
                onRemoveShaderCaches = { showDeleteShaderConfirmationDialog = true },
                onAboutTitle = { goToGameDetails(game) },
                onCreateShortcut = { createShortcut(game) },
            )

            if (showDeleteShaderConfirmationDialog) {
                ShaderCachesConfirmationDialog(
                    gameName = game.name ?: "",
                    onDismissRequest = { showDeleteShaderConfirmationDialog = false },
                    onConfirm = {
                        deleteShaderCaches(game)
                        showDeleteShaderConfirmationDialog = false
                    },
                )
            }
        }
    }
}

@Composable
private fun GameGridItem(
    onStartGame: (Game) -> Unit,
    onIsFavoriteChanged: (Boolean) -> Unit,
    onEditGameProfile: () -> Unit,
    onRemoveShaderCaches: () -> Unit,
    onAboutTitle: () -> Unit,
    onCreateShortcut: () -> Unit,
    game: Game,
    imageAspectRatio: Float,
    imageContent: @Composable (Game, Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    var contextMenuExpanded by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = modifier
            .combinedClickable(
                onClick = { onStartGame(game) },
                onLongClick = { contextMenuExpanded = true },
            )
            .padding(4.dp),
    ) {
        Box {
            imageContent(
                game,
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(imageAspectRatio)
                    .clip(RoundedCornerShape(8.dp)),
            )
            if (game.isFavorite) {
                Icon(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    painter = painterResource(R.drawable.ic_favorite),
                    tint = MaterialTheme.colorScheme.primary,
                    contentDescription = null
                )
            }
            GameContextMenu(
                expanded = contextMenuExpanded,
                onDismissRequest = { contextMenuExpanded = false },
                game = game,
                onIsFavoriteChanged = onIsFavoriteChanged,
                onEditGameProfile = onEditGameProfile,
                onRemoveShaderCaches = onRemoveShaderCaches,
                onAboutTitle = onAboutTitle,
                onCreateShortcut = onCreateShortcut,
            )
        }
        Text(
            modifier = Modifier.padding(top = 4.dp),
            text = game.name ?: "",
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ShaderCachesConfirmationDialog(
    gameName: String,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        title = { Text(tr("Remove shader caches")) },
        text = { Text(tr("Remove the shader caches for {0}?", gameName)) },
        dismissButton = { TextButton(onClick = onDismissRequest) { Text(tr("No")) } },
        onDismissRequest = onDismissRequest,
        confirmButton = { TextButton(onClick = onConfirm) { Text(tr("Yes")) } },
    )
}

@Composable
private fun GameListItem(
    onStartGame: (Game) -> Unit,
    onIsFavoriteChanged: (Boolean) -> Unit,
    onEditGameProfile: () -> Unit,
    onRemoveShaderCaches: () -> Unit,
    onAboutTitle: () -> Unit,
    onCreateShortcut: () -> Unit,
    game: Game,
    modifier: Modifier = Modifier,
) {
    var contextMenuExpanded by rememberSaveable { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .combinedClickable(
                onClick = { onStartGame(game) },
                onLongClick = {
                    contextMenuExpanded = true
                },
            )
            .padding(8.dp)
            .fillMaxWidth(),
    ) {
        Box {
            GameIcon(
                game = game,
                modifier = Modifier.size(60.dp),
            )
            if (game.isFavorite) {
                Icon(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    painter = painterResource(R.drawable.ic_favorite),
                    tint = MaterialTheme.colorScheme.primary,
                    contentDescription = null
                )
            }
        }
        Text(
            modifier = Modifier.padding(horizontal = 8.dp),
            text = game.name ?: "",
            fontSize = 24.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        GameContextMenu(
            expanded = contextMenuExpanded,
            onDismissRequest = { contextMenuExpanded = false },
            game = game,
            onIsFavoriteChanged = onIsFavoriteChanged,
            onEditGameProfile = onEditGameProfile,
            onRemoveShaderCaches = onRemoveShaderCaches,
            onAboutTitle = onAboutTitle,
            onCreateShortcut = onCreateShortcut,
        )
    }
}

@Composable
private fun GameContextMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onIsFavoriteChanged: (Boolean) -> Unit,
    onEditGameProfile: () -> Unit,
    onRemoveShaderCaches: () -> Unit,
    onAboutTitle: () -> Unit,
    onCreateShortcut: () -> Unit,
    game: Game,
) {
    @Composable
    fun GameContextMenuItem(
        onClick: () -> Unit,
        text: String,
        enabled: Boolean = true,
        trailingIcon: @Composable (() -> Unit)? = null,
    ) {
        DropdownMenuItem(
            enabled = enabled,
            onClick = {
                onDismissRequest()
                onClick()
            },
            text = {
                Text(text = text)
            },
            trailingIcon = trailingIcon,
        )
    }
    DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest) {
        val gameTitleHasCaches = rememberSaveable {
            NativeGameTitles.titleHasShaderCacheFiles(game.titleId)
        }
        GameContextMenuItem(
            onClick = { onIsFavoriteChanged(!game.isFavorite) },
            text = tr("Favorite"),
            trailingIcon = { Checkbox(checked = game.isFavorite, onCheckedChange = null) },
        )
        GameContextMenuItem(
            onClick = onEditGameProfile,
            text = tr("Edit game profile"),
        )
        GameContextMenuItem(
            enabled = gameTitleHasCaches,
            onClick = onRemoveShaderCaches,
            text = tr("Remove shader caches")
        )
        GameContextMenuItem(
            onClick = onAboutTitle,
            text = tr("About title"),
        )
        GameContextMenuItem(
            onClick = onCreateShortcut,
            text = tr("Create shortcut"),
        )
    }
}

/**
 * The AEX-style top bar: L1/R1 (wired in [GamesListScreen]) cycle through
 * these same four destinations, so the visible tab row and the bumpers agree
 * on one order instead of the bumpers driving state the bar doesn't show.
 */
@Composable
private fun GamesTopBarTabs(
    tabs: List<Pair<String, () -> Unit>>,
    currentIndex: Int,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        tabs.forEachIndexed { index, (label, onClick) ->
            val current = index == currentIndex
            Text(
                text = label,
                fontSize = 16.sp,
                fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                color = if (current) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = !current, onClick = onClick)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun GameListToolBarActionsMenu(
    openCemuFolder: () -> Unit,
    shareLogFile: () -> Unit,
    goToAboutCemu: () -> Unit,
) {
    var expandMenu by remember { mutableStateOf(false) }

    @Composable
    fun DropdownMenuItem(onClick: () -> Unit, text: String) {
        MaterialDropdownMenuItem(
            onClick = {
                onClick()
                expandMenu = false
            },
            text = { Text(text) },
        )
    }
    IconButton(
        modifier = Modifier.padding(end = 8.dp),
        onClick = { expandMenu = true },
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_more_vert),
            contentDescription = null
        )
    }

    DropdownMenu(
        expanded = expandMenu,
        onDismissRequest = { expandMenu = false }
    ) {
        DropdownMenuItem(
            onClick = openCemuFolder,
            text = tr("Open Cemu folder")
        )
        DropdownMenuItem(
            onClick = shareLogFile,
            text = tr("Share log file"),
        )
        DropdownMenuItem(
            onClick = goToAboutCemu,
            text = tr("About Cemu"),
        )
    }
}

private const val LOG_FILE_NAME = "log.txt"

private fun logFileExists(): Boolean {
    return File(NativeActiveSettings.getUserDataPath()).resolve(LOG_FILE_NAME).isFile
}

private fun tryShareLogFile(context: Context): Boolean {
    try {
        val fileUri = DocumentsContract.buildDocumentUri(
            DocumentsProvider.AUTHORITY,
            DocumentsProvider.ROOT_ID + "/$LOG_FILE_NAME"
        )

        val documentFile = DocumentFile.fromSingleUri(context, fileUri)!!

        val intent = Intent(Intent.ACTION_SEND)
            .setDataAndType(documentFile.uri, "text/plain")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .putExtra(Intent.EXTRA_STREAM, documentFile.uri)

        context.startActivity(Intent.createChooser(intent, null))

        return true
    } catch (_: Exception) {
        return false
    }
}

private fun tryOpenCemuFolder(context: Context): Boolean {
    try {
        val intent = Intent(Intent.ACTION_VIEW)
            .addCategory(Intent.CATEGORY_DEFAULT)
            .addFlags(
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                        or Intent.FLAG_GRANT_READ_URI_PERMISSION
                        or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
                        or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        intent.data = DocumentsContract.buildRootUri(
            DocumentsProvider.AUTHORITY,
            DocumentsProvider.ROOT_ID
        )
        context.startActivity(intent)

        return true
    } catch (_: Exception) {
        return false
    }
}
