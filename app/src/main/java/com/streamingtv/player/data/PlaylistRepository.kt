package com.streamingtv.player.data

import com.streamingtv.player.data.m3u.M3uParser
import com.streamingtv.player.data.stalker.StalkerClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** A category together with the items shown in its row. */
data class Section(
    val category: Category,
    val items: List<Channel>
)

/** Everything the browse screen needs: live + VOD sections. */
data class Playlist(
    val liveCategories: List<Category>,
    val liveChannels: List<Channel>,
    val vodSections: List<Section> = emptyList()
) {
    fun channelsFor(categoryId: String): List<Channel> =
        liveChannels.filter { it.categoryId == categoryId }

    /** Flat list of everything playable, used by search. */
    val allItems: List<Channel> get() = liveChannels + vodSections.flatMap { it.items }
}

/**
 * Single entry point the UI uses to load content and resolve stream URLs,
 * regardless of whether the provider is a Stalker portal or an M3U playlist.
 */
class PlaylistRepository(private val prefs: Prefs) {

    private var stalker: StalkerClient? = null

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun load(): Playlist = when (prefs.sourceType) {
        SourceType.STALKER_PORTAL -> loadStalker()
        SourceType.M3U_PLAYLIST -> loadM3u()
    }

    /** Returns a directly playable URL, resolving Stalker commands if needed. */
    suspend fun resolvePlayUrl(channel: Channel): String {
        if (!channel.needsResolve) return channel.streamUrl
        return stalkerClient().resolveStreamUrl(channel)
    }

    /** Now/next EPG for a live channel (empty for M3U or on failure). */
    suspend fun loadEpg(channel: Channel): List<EpgProgram> {
        if (prefs.sourceType != SourceType.STALKER_PORTAL || channel.isVod) return emptyList()
        return runCatching { stalkerClient().getShortEpg(channel.id) }.getOrDefault(emptyList())
    }

    private suspend fun stalkerClient(): StalkerClient {
        stalker?.let { return it }
        return StalkerClient(prefs.portalUrl, prefs.macAddress).also {
            it.connect()
            stalker = it
        }
    }

    private suspend fun loadStalker(): Playlist {
        // Reconnect fresh so a reload picks up new credentials / session state.
        stalker = null
        val client = stalkerClient()
        val categories = client.getCategories().filterNot { it.id == "*" }
        val channels = client.getChannels()
        // VOD is best-effort: never let it break live TV.
        val vodSections = runCatching {
            client.getVodCategories().take(MAX_VOD_ROWS).mapNotNull { cat ->
                val items = runCatching { client.getVodItems(cat.id) }.getOrDefault(emptyList())
                if (items.isEmpty()) null else Section(cat, items)
            }
        }.getOrDefault(emptyList())
        return Playlist(categories, channels, vodSections)
    }

    private suspend fun loadM3u(): Playlist = withContext(Dispatchers.IO) {
        val url = prefs.m3uUrl
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "StreamingTV/1.0")
            .build()
        val body = http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) error("M3U konnte nicht geladen werden (HTTP ${resp.code}).")
            resp.body?.string().orEmpty()
        }
        val result = M3uParser.parse(body)
        Playlist(result.categories, result.channels)
    }

    companion object {
        private const val MAX_VOD_ROWS = 8
    }
}
