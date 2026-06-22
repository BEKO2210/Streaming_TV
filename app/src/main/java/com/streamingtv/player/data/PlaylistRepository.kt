package com.streamingtv.player.data

import com.streamingtv.player.data.m3u.M3uParser
import com.streamingtv.player.data.stalker.StalkerClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Bundles categories with their channels for the UI. */
data class Playlist(
    val categories: List<Category>,
    val channels: List<Channel>
) {
    fun channelsFor(categoryId: String): List<Channel> =
        channels.filter { it.categoryId == categoryId }
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
        val client = stalker ?: StalkerClient(prefs.portalUrl, prefs.macAddress).also {
            it.connect()
            stalker = it
        }
        return client.resolveStreamUrl(channel)
    }

    private suspend fun loadStalker(): Playlist {
        val client = StalkerClient(prefs.portalUrl, prefs.macAddress)
        client.connect()
        stalker = client
        val categories = client.getCategories().filterNot { it.id == "*" }
        val channels = client.getChannels()
        return Playlist(categories, channels)
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
}
