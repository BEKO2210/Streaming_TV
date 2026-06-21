package com.streamingtv.player.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.lifecycle.lifecycleScope
import com.streamingtv.player.R
import com.streamingtv.player.data.Channel
import com.streamingtv.player.data.Playlist
import com.streamingtv.player.data.PlaylistRepository
import com.streamingtv.player.data.Prefs
import com.streamingtv.player.data.SourceType
import com.streamingtv.player.ui.playback.PlaybackActivity
import com.streamingtv.player.ui.settings.SettingsActivity
import kotlinx.coroutines.launch

class MainFragment : BrowseSupportFragment() {

    private lateinit var prefs: Prefs
    private lateinit var repo: PlaylistRepository
    private val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(requireContext())
        repo = PlaylistRepository(prefs)

        title = getString(R.string.app_name)
        headersState = HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = true
        adapter = rowsAdapter

        setOnSearchClickedListener { openSettings() }
        // Use the search affordance area as a quick "Settings" entry.
        searchAffordanceColor = resources.getColor(R.color.brand_accent, null)

        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            if (item is Channel) playChannel(item)
        }
    }

    override fun onResume() {
        super.onResume()
        if (!prefs.isConfigured) {
            promptSetup()
        } else {
            loadContent()
        }
    }

    private fun promptSetup() {
        rowsAdapter.clear()
        Toast.makeText(
            requireContext(),
            getString(R.string.toast_configure_first),
            Toast.LENGTH_LONG
        ).show()
        openSettings()
    }

    private fun loadContent() {
        viewLifecycleOwner.lifecycleScope.launch {
            rowsAdapter.clear()
            try {
                val playlist = repo.load()
                buildRows(playlist)
                if (playlist.channels.isEmpty()) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.toast_no_channels),
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    e.message ?: getString(R.string.toast_load_failed),
                    Toast.LENGTH_LONG
                ).show()
                openSettings()
            }
        }
    }

    private fun buildRows(playlist: Playlist) {
        val cardPresenter = CardPresenter()
        val categories = playlist.categories.ifEmpty {
            // M3U without groups, or portal without genres: one combined row.
            listOf(com.streamingtv.player.data.Category("all", getString(R.string.row_all_channels)))
        }
        for (category in categories) {
            val channels = if (playlist.categories.isEmpty()) {
                playlist.channels
            } else {
                playlist.channelsFor(category.id)
            }
            if (channels.isEmpty()) continue
            val listRowAdapter = ArrayObjectAdapter(cardPresenter)
            channels.forEach { listRowAdapter.add(it) }
            rowsAdapter.add(ListRow(HeaderItem(category.title), listRowAdapter))
        }
    }

    private fun playChannel(channel: Channel) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val url = repo.resolvePlayUrl(channel)
                val intent = Intent(requireContext(), PlaybackActivity::class.java).apply {
                    putExtra(PlaybackActivity.EXTRA_URL, url)
                    putExtra(PlaybackActivity.EXTRA_TITLE, channel.name)
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    e.message ?: getString(R.string.toast_play_failed),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun openSettings() {
        startActivity(Intent(requireContext(), SettingsActivity::class.java))
    }
}
