package com.streamingtv.player.data.stalker

import com.streamingtv.player.data.Category
import com.streamingtv.player.data.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Client for the Stalker / Ministra (MAG STB) portal protocol.
 *
 * The provider ("Betreiber") activates the line for a specific MAC address.
 * This client identifies itself with that MAC via cookies, performs the
 * handshake to obtain a token, then loads genres + channels and resolves the
 * real stream URL on demand through `create_link`.
 */
class StalkerClient(
    portalUrl: String,
    private val mac: String
) {
    private val baseUrl: String = normalizePortal(portalUrl)
    private var token: String? = null

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /** Performs handshake + get_profile. Throws on failure. */
    suspend fun connect() = withContext(Dispatchers.IO) {
        val handshake = call("type=stb&action=handshake&token=&JsHttpRequest=1-xml")
        token = handshake.optJSONObject("js")?.optString("token")?.takeIf { it.isNotBlank() }
            ?: throw StalkerException("Handshake fehlgeschlagen – Portal antwortet ohne Token. Prüfe Portal-URL und MAC.")
        // Some portals require get_profile before serving content.
        runCatching {
            call("type=stb&action=get_profile&hd=1&JsHttpRequest=1-xml")
        }
        Unit
    }

    suspend fun getCategories(): List<Category> = withContext(Dispatchers.IO) {
        val resp = call("type=itv&action=get_genres&JsHttpRequest=1-xml")
        val arr = resp.optJSONArray("js") ?: return@withContext emptyList()
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id")
            if (id.isBlank()) null else Category(id, o.optString("title", "Genre $id"))
        }
    }

    /** Loads all live channels via get_all_channels (single page). */
    suspend fun getChannels(): List<Channel> = withContext(Dispatchers.IO) {
        val resp = call("type=itv&action=get_all_channels&JsHttpRequest=1-xml")
        val data = resp.optJSONObject("js")?.optJSONArray("data") ?: return@withContext emptyList()
        (0 until data.length()).mapNotNull { i ->
            val o = data.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id")
            if (id.isBlank()) return@mapNotNull null
            Channel(
                id = id,
                name = o.optString("name", "Channel $id"),
                number = o.optString("number").takeIf { it.isNotBlank() },
                logoUrl = o.optString("logo").takeIf { it.isNotBlank() }?.let { absUrl(it) },
                categoryId = o.optString("tv_genre_id").takeIf { it.isNotBlank() },
                cmd = o.optString("cmd").takeIf { it.isNotBlank() }
            )
        }
    }

    /**
     * Resolves the playable URL for a channel through `create_link`.
     * Portals usually return something like "ffmpeg http://real/stream";
     * we strip the leading prefix and return the bare URL.
     */
    suspend fun resolveStreamUrl(channel: Channel): String = withContext(Dispatchers.IO) {
        val cmd = channel.cmd ?: throw StalkerException("Kanal hat kein abspielbares Kommando.")
        val encoded = URLEncoder.encode(cmd, "UTF-8")
        val resp = call("type=itv&action=create_link&cmd=$encoded&JsHttpRequest=1-xml")
        val raw = resp.optJSONObject("js")?.optString("cmd").orEmpty()
        if (raw.isBlank()) throw StalkerException("Stream-Link konnte nicht aufgelöst werden.")
        // Strip leading tokens like "ffmpeg " or "auto " and surrounding spaces.
        raw.trim().split(" ").lastOrNull { it.startsWith("http", ignoreCase = true) }
            ?: raw.trim()
    }

    private fun call(query: String): JSONObject {
        val url = "${baseUrl}portal.php?$query"
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Referer", baseUrl)
            .header("Accept", "*/*")
            .header("X-User-Agent", "Model: MAG250; Link: WiFi")
            .header("Cookie", "mac=$mac; stb_lang=en; timezone=Europe/Berlin")
        token?.let { builder.header("Authorization", "Bearer $it") }

        http.newCall(builder.build()).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw StalkerException("Portal-Fehler ${resp.code}. Prüfe URL/MAC und ob die Linie aktiv ist.")
            }
            return runCatching { JSONObject(body) }
                .getOrElse { throw StalkerException("Ungültige Antwort vom Portal.") }
        }
    }

    private fun absUrl(path: String): String {
        if (path.startsWith("http", ignoreCase = true)) return path
        val root = baseUrl.removeSuffix("c/").removeSuffix("/")
        return "$root/${path.removePrefix("/")}"
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) " +
                "MAG200 stbapp ver: 2 rev: 250 Safari/533.3"

        /** Ensures the portal URL ends in the "/c/" style base used by portal.php. */
        fun normalizePortal(input: String): String {
            var url = input.trim()
            if (!url.startsWith("http", ignoreCase = true)) url = "http://$url"
            val parsed = url.toHttpUrlOrNull() ?: return url.removeSuffix("/") + "/c/"
            // If the user already pointed at a portal path, keep its directory.
            val path = parsed.encodedPath
            val base = when {
                path.contains("portal.php") -> path.substringBeforeLast("portal.php")
                path.endsWith("/c/") -> path
                path.endsWith("/c") -> "$path/"
                path.endsWith("/") -> "${path}c/"
                path.isBlank() || path == "/" -> "/c/"
                else -> "$path/c/"
            }
            return "${parsed.scheme}://${parsed.host}${if (parsed.port in 1..65534 && parsed.port != 80 && parsed.port != 443) ":${parsed.port}" else ""}$base"
        }
    }
}

class StalkerException(message: String) : Exception(message)
