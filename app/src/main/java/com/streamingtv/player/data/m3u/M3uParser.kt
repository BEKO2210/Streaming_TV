package com.streamingtv.player.data.m3u

import com.streamingtv.player.data.Category
import com.streamingtv.player.data.Channel

/** Result of parsing an M3U playlist into categories + channels. */
data class M3uResult(
    val categories: List<Category>,
    val channels: List<Channel>
)

/**
 * Minimal but robust parser for extended M3U (#EXTM3U) playlists as produced
 * by most IPTV providers. Understands tvg-id, tvg-name, tvg-logo and
 * group-title attributes.
 */
object M3uParser {

    private val ATTR_REGEX = Regex("""(\S+?)="(.*?)"""")

    fun parse(content: String): M3uResult {
        val channels = mutableListOf<Channel>()
        val categories = LinkedHashMap<String, Category>()

        var pendingName: String? = null
        var pendingLogo: String? = null
        var pendingGroup: String? = null
        var pendingId: String? = null
        var index = 0

        content.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val attrs = ATTR_REGEX.findAll(line).associate { it.groupValues[1] to it.groupValues[2] }
                    pendingId = attrs["tvg-id"]?.takeIf { it.isNotBlank() }
                    pendingLogo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() }
                    pendingGroup = attrs["group-title"]?.takeIf { it.isNotBlank() }
                    // Display name is everything after the last comma.
                    pendingName = line.substringAfterLast(",").trim()
                        .ifBlank { attrs["tvg-name"] }
                }
                line.isBlank() || line.startsWith("#") -> {
                    // Skip comments / other directives.
                }
                else -> {
                    val name = pendingName ?: "Channel ${index + 1}"
                    val groupTitle = pendingGroup ?: "All"
                    val categoryId = "grp_" + groupTitle.lowercase().hashCode()
                    categories.getOrPut(categoryId) { Category(categoryId, groupTitle) }
                    channels.add(
                        Channel(
                            id = pendingId ?: "ch_$index",
                            name = name,
                            logoUrl = pendingLogo,
                            categoryId = categoryId,
                            streamUrl = line
                        )
                    )
                    index++
                    pendingName = null
                    pendingLogo = null
                    pendingGroup = null
                    pendingId = null
                }
            }
        }

        return M3uResult(categories.values.toList(), channels)
    }
}
