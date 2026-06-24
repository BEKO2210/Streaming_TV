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
    val start: String?,
    val end: String?
) : Serializable

/** Which kind of provider ("Betreiber") the user is connecting to. */
enum class SourceType {
    STALKER_PORTAL,
    M3U_PLAYLIST
}
