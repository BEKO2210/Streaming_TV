package com.streamingtv.player.data

import java.io.Serializable

/** A group/category of channels (Stalker "genre" or M3U "group-title"). */
data class Category(
    val id: String,
    val title: String
) : Serializable

/**
 * A single live channel.
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
    val cmd: String? = null
) : Serializable {
    val needsResolve: Boolean get() = streamUrl.isBlank() && !cmd.isNullOrBlank()
}

/** Which kind of provider ("Betreiber") the user is connecting to. */
enum class SourceType {
    STALKER_PORTAL,
    M3U_PLAYLIST
}
