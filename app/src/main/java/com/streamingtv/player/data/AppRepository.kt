package com.streamingtv.player.data

import android.content.Context

/**
 * Process-wide holder for the single [PlaylistRepository] (and its connected
 * Stalker session) plus the most recently loaded [Playlist]. Sharing one
 * instance lets the details, search and playback screens reuse the live
 * portal session and avoid reloading the whole catalog.
 */
object AppRepository {

    private var repo: PlaylistRepository? = null

    /** Cached catalog from the last successful load. */
    @Volatile
    var playlist: Playlist? = null

    fun get(context: Context): PlaylistRepository =
        repo ?: PlaylistRepository(Prefs(context.applicationContext)).also { repo = it }

    /** Drops the cached session, e.g. after the user changes the provider. */
    fun reset(context: Context) {
        repo = PlaylistRepository(Prefs(context.applicationContext))
        playlist = null
    }

    fun findById(id: String): Channel? =
        playlist?.allItems?.firstOrNull { it.id == id }
}
