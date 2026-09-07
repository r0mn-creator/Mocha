package info.cemu.cemu.games.boxart

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import android.graphics.BitmapFactory
import info.cemu.cemu.games.GameIcon
import info.cemu.cemu.nativeinterface.NativeGameTitles

/**
 * Real cover art for the box-art grid view, backed by [LibretroBoxArt].
 * Falls back to the same small native icon the list view uses - both while
 * a fetch is in flight and permanently for the titles libretro has no art
 * for - rather than leaving a blank tile.
 */
@Composable
fun BoxArtImage(
    game: NativeGameTitles.Game,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var bitmap by remember(game.titleId) {
        mutableStateOf(LibretroBoxArt.loadCached(context, game.titleId))
    }

    LaunchedEffect(game.titleId) {
        if (bitmap != null) return@LaunchedEffect
        val file = LibretroBoxArt.findAndCache(context, game) ?: return@LaunchedEffect
        bitmap = BitmapFactory.decodeFile(file.path)
    }

    val loaded: Bitmap? = bitmap
    if (loaded != null) {
        Image(
            bitmap = loaded.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    } else {
        GameIcon(game = game, modifier = modifier)
    }
}
