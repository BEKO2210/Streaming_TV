package com.streamingtv.player.data

import android.content.Context
import androidx.core.content.edit
import java.util.Locale
import kotlin.random.Random

/**
 * Persists the user's provider configuration.
 *
 * The MAC address is the key piece of information the user shares with their
 * provider ("Betreiber") so the line can be activated on the portal.
 */
class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("streaming_tv_prefs", Context.MODE_PRIVATE)

    var sourceType: SourceType
        get() = runCatching { SourceType.valueOf(sp.getString(KEY_SOURCE_TYPE, null) ?: "") }
            .getOrDefault(SourceType.STALKER_PORTAL)
        set(value) = sp.edit { putString(KEY_SOURCE_TYPE, value.name) }

    /** Stalker / MAG portal URL, e.g. http://server:port/c/ */
    var portalUrl: String
        get() = sp.getString(KEY_PORTAL_URL, "") ?: ""
        set(value) = sp.edit { putString(KEY_PORTAL_URL, value.trim()) }

    /** MAC address used to identify this "box" to the provider. */
    var macAddress: String
        get() {
            val existing = sp.getString(KEY_MAC, null)
            if (!existing.isNullOrBlank()) return existing
            val generated = generateMac()
            macAddress = generated
            return generated
        }
        set(value) = sp.edit { putString(KEY_MAC, value.trim().uppercase(Locale.ROOT)) }

    var m3uUrl: String
        get() = sp.getString(KEY_M3U_URL, "") ?: ""
        set(value) = sp.edit { putString(KEY_M3U_URL, value.trim()) }

    val isConfigured: Boolean
        get() = when (sourceType) {
            SourceType.STALKER_PORTAL -> portalUrl.isNotBlank() && macAddress.isNotBlank()
            SourceType.M3U_PLAYLIST -> m3uUrl.isNotBlank()
        }

    /** IDs of channels the user marked as favorites. */
    var favorites: Set<String>
        get() = sp.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()
        private set(value) = sp.edit { putStringSet(KEY_FAVORITES, value) }

    fun isFavorite(id: String): Boolean = favorites.contains(id)

    /** Toggles favorite state and returns the new state (true = now favorite). */
    fun toggleFavorite(id: String): Boolean {
        val current = favorites.toMutableSet()
        val added = if (current.contains(id)) {
            current.remove(id); false
        } else {
            current.add(id); true
        }
        favorites = current
        return added
    }

    companion object {
        private const val KEY_SOURCE_TYPE = "source_type"
        private const val KEY_PORTAL_URL = "portal_url"
        private const val KEY_MAC = "mac_address"
        private const val KEY_M3U_URL = "m3u_url"
        private const val KEY_FAVORITES = "favorites"

        /**
         * Generates a random MAC address using the 00:1A:79 prefix that MAG
         * STB devices use. Providers expect this prefix, so generated MACs are
         * accepted when handed to the operator for activation.
         */
        fun generateMac(): String {
            val tail = (0 until 3).joinToString(":") {
                String.format(Locale.ROOT, "%02X", Random.nextInt(0, 256))
            }
            return "00:1A:79:$tail"
        }
    }
}
