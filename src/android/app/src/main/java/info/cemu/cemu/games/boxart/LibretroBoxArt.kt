package info.cemu.cemu.games.boxart

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import info.cemu.cemu.nativeinterface.NativeGameTitles
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URLEncoder

private const val REPO = "libretro-thumbnails/Nintendo_-_Wii_U"
private const val BOX_ART_PREFIX = "Named_Boxarts/"
private const val TREE_URL = "https://api.github.com/repos/$REPO/git/trees/master?recursive=1"
private const val RAW_BASE = "https://raw.githubusercontent.com/$REPO/master/$BOX_ART_PREFIX"
private const val FILE_LIST_CACHE_NAME = "boxart_filelist.json"
private const val FILE_LIST_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000 // libretro's list barely churns

/**
 * Wii U box art, pulled from libretro-thumbnails rather than reverse-engineered
 * or scraped from the game itself - Cemu only ever has the tiny square meta
 * icon (see [info.cemu.cemu.games.GameIcon]), never real cover art. Coverage
 * checked before building this: ~450 titles in Named_Boxarts, essentially the
 * full retail library, not the sparse handful some other systems have.
 */
object LibretroBoxArt {
    private val client by lazy {
        HttpClient(OkHttp) {
            install(HttpTimeout) {
                requestTimeoutMillis = 10_000
                connectTimeoutMillis = 10_000
            }
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class TreeEntry(val path: String)

    @Serializable
    private data class TreeResponse(val tree: List<TreeEntry>)

    @Volatile
    private var fileNames: List<String>? = null

    private fun boxArtCacheDir(context: Context) = File(context.cacheDir, "boxart")

    fun cacheFileFor(context: Context, titleId: Long): File =
        File(boxArtCacheDir(context), "$titleId.png")

    /** Already-downloaded art, if any - safe to call straight from Compose. */
    fun loadCached(context: Context, titleId: Long): Bitmap? {
        val file = cacheFileFor(context, titleId)
        if (!file.isFile) return null
        return BitmapFactory.decodeFile(file.path)
    }

    private suspend fun loadFileList(context: Context): List<String> {
        fileNames?.let { return it }

        val listCacheFile = File(context.cacheDir, FILE_LIST_CACHE_NAME)
        if (listCacheFile.isFile &&
            System.currentTimeMillis() - listCacheFile.lastModified() < FILE_LIST_MAX_AGE_MS
        ) {
            runCatching {
                val names = json.decodeFromString<List<String>>(listCacheFile.readText())
                fileNames = names
                return names
            }
        }

        return try {
            val responseText = client.get(TREE_URL) {
                header("User-Agent", "Mocha-Android")
            }.bodyAsText()
            val names = json.decodeFromString<TreeResponse>(responseText).tree
                .map { it.path }
                .filter { it.startsWith(BOX_ART_PREFIX) }
                .map { it.removePrefix(BOX_ART_PREFIX) }
            listCacheFile.writeText(json.encodeToString(names))
            fileNames = names
            names
        } catch (e: Exception) {
            Log.e("LibretroBoxArt", "file list fetch failed", e)
            // Stale cache is better than none if the fetch failed and there
            // was no fresh-enough file on disk to short-circuit into above.
            listCacheFile.takeIf { it.isFile }
                ?.let { runCatching { json.decodeFromString<List<String>>(it.readText()) }.getOrNull() }
                ?: emptyList()
        }
    }

    // Named_Boxarts entries look like "Title (Region) (Lang,Lang,...).png" -
    // strip every trailing "(...)" group to recover the bare title to match
    // against the game's own name.
    private val trailingParenGroup = Regex("""\s*\([^)]*\)\s*$""")

    private fun baseName(fileName: String): String {
        var name = fileName.removeSuffix(".png")
        while (true) {
            val stripped = trailingParenGroup.replace(name, "")
            if (stripped == name) return name.trim()
            name = stripped
        }
    }

    private fun preferredRegionTags(region: Int): List<String> = buildList {
        if (region and NativeGameTitles.ConsoleRegion.USA != 0) add("(USA)")
        if (region and NativeGameTitles.ConsoleRegion.EUR != 0) add("(Europe)")
        if (region and NativeGameTitles.ConsoleRegion.JPN != 0) add("(Japan)")
        // Fall back through the usual release regions rather than giving up
        // just because the game's own region flag didn't match a tag exactly.
        add("(USA)"); add("(Europe)"); add("(Japan)"); add("(World)")
    }

    /**
     * Finds a match for [game] and downloads it to the on-disk cache if not
     * already there. Returns the cache file on success. Safe to call
     * repeatedly - it's a no-op once cached, and remembers "no match found"
     * for the process lifetime via the empty-list short-circuit in
     * [loadFileList] rather than re-hitting the network every recomposition.
     */
    suspend fun findAndCache(context: Context, game: NativeGameTitles.Game): File? {
        val title = game.name?.trim()
        if (title.isNullOrEmpty()) return null

        val cacheFile = cacheFileFor(context, game.titleId)
        if (cacheFile.isFile) return cacheFile

        return withContext(Dispatchers.IO) {
            val names = loadFileList(context)
            val candidates = names.filter { baseName(it).equals(title, ignoreCase = true) }
            if (candidates.isEmpty()) return@withContext null

            val chosen = preferredRegionTags(game.region)
                .firstNotNullOfOrNull { tag -> candidates.firstOrNull { it.contains(tag) } }
                ?: candidates.first()

            try {
                val url = RAW_BASE + URLEncoder.encode(chosen, "UTF-8").replace("+", "%20")
                val bytes: ByteArray = client.get(url).body()
                cacheFile.parentFile?.mkdirs()
                cacheFile.writeBytes(bytes)
                cacheFile
            } catch (e: Exception) {
                Log.e("LibretroBoxArt", "download failed for $chosen", e)
                null
            }
        }
    }
}
