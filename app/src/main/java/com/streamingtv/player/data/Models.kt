package com.streamingtv.player.data

import java.io.Serializable

/** A group/category of channels (Stalker "genre" or M3U "group-title"). */
data class Category(
    val id: String,
    val title: String
) : Serializable

/** What kind of content an item represents. */
enum class ContentType { LIVE, VOD }

/**
 * A single playable item (live channel or VOD title).
 *
 * For Stalker portals the actual stream URL is resolved lazily via
 * `create_link`, so [streamUrl] may be empty and [cmd] holds the portal
 * command that has to be resolved before playback.
 */
data class Channel(
    val id: String,
    val name: String,
    val number: String? = null,
    val logoUrl: String? = null,
    val categoryId: String? = null,
    val streamUrl: String = "",
    val cmd: String? = null,
    val contentType: ContentType = ContentType.LIVE,
    val description: String? = null
) : Serializable {
    val needsResolve: Boolean get() = streamUrl.isBlank() && !cmd.isNullOrBlank()
    val isVod: Boolean get() = contentType == ContentType.VOD
}

/** A single EPG (electronic program guide) entry. */
data class EpgProgram(
    val title: String,
    val description: String?,
    val startMs: Long?,
    val endMs: Long?
) : Serializable {
    /** Playback progress 0..1 for [now], or null if timing is unknown. */
    fun progressAt(now: Long): Float? {
        val s = startMs ?: return null
        val e = endMs ?: return null
        if (e <= s) return null
        return ((now - s).toFloat() / (e - s)).coerceIn(0f, 1f)
    }
}

/** Which kind of provider ("Betreiber") the user is connecting to. */
enum class SourceType {
    STALKER_PORTAL,
    M3U_PLAYLIST
}
