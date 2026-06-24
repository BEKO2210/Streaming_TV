package com.streamingtv.player.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.leanback.app.BackgroundManager
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.lifecycle.lifecycleScope
import com.streamingtv.player.R
import com.streamingtv.player.data.AppRepository
import com.streamingtv.player.data.Category
import com.streamingtv.player.data.Channel
import com.streamingtv.player.data.Playlist
import com.streamingtv.player.data.Prefs
import com.streamingtv.player.ui.playback.PlaybackActivity
import com.streamingtv.player.ui.search.SearchActivity
import com.streamingtv.player.ui.settings.SettingsActivity
import kotlinx.coroutines.launch

class MainFragment : BrowseSupportFragment() {

    private lateinit var prefs: Prefs
    private val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
    private var lastConfigSignature: String? = null
    private var lastRenderSignature: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(requireContext())

        title = getString(R.string.app_name)
        headersState = HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = true
        brandColor = ContextCompat.getColor(requireContext(), R.color.brand_surface)
        searchAffordanceColor = ContextCompat.getColor(requireContext(), R.color.brand_accent)
        adapter = rowsAdapter

        setupBackground()

        setOnSearchClickedListener {
            startActivity(Intent(requireContext(), SearchActivity::class.java))
        }

        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            when (item) {
                is Channel -> playChannel(item)
                is MenuItem -> onMenuItem(item)
            }
        }
    }

    private fun setupBackground() {
        val bm = BackgroundManager.getInstance(requireActivity())
        if (!bm.isAttached) bm.attach(requireActivity().window)
        bm.drawable = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                ContextCompat.getColor(requireContext(), R.color.brand_background),
                Color.BLACK
            )
        )
    }

    override fun onResume() {
        super.onResume()
        val signature = configSignature()
        when {
            !prefs.isConfigured -> promptSetup()
            AppRepository.playlist != null && signature == lastConfigSignature -> {
                // Only rebuild (which resets scroll position) when favorites
                // actually changed; otherwise keep the grid as the user left it.
                if (renderSignature() != lastRenderSignature) buildRows(AppRepository.playlist!!)
            }
            else -> loadContent(signature)
        }
    }

    private fun configSignature() =
        "${prefs.sourceType}|${prefs.portalUrl}|${prefs.macAddress}|${prefs.m3uUrl}"

    private fun renderSignature() =
        prefs.favorites.sorted().joinToString(",") + "#" + prefs.recentIds().joinToString(",")

    private fun promptSetup() {
        rowsAdapter.clear()
        Toast.makeText(requireContext(), getString(R.string.toast_configure_first), Toast.LENGTH_LONG).show()
        openSettings()
    }

    private fun loadContent(signature: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            rowsAdapter.clear()
            progressBarManager.show()
            try {
                val playlist = AppRepository.get(requireContext()).load()
                AppRepository.playlist = playlist
                lastConfigSignature = signature
                buildRows(playlist)
                if (playlist.allItems.isEmpty()) {
                    Toast.makeText(requireContext(), getString(R.string.toast_no_channels), Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    e.message ?: getString(R.string.toast_load_failed),
                    Toast.LENGTH_LONG
                ).show()
                openSettings()
            } finally {
                progressBarManager.hide()
            }
        }
    }

    private fun buildRows(playlist: Playlist) {
        rowsAdapter.clear()
        val cardPresenter = CardPresenter(prefs)
        val byId = playlist.allItems.associateBy { it.id }

        // Recently watched (keeps the most-recent order).
        val recents = prefs.recentIds().mapNotNull { byId[it] }
        if (recents.isNotEmpty()) {
            rowsAdapter.add(row(getString(R.string.row_recent), recents, cardPresenter))
        }

        // Favorites.
        val favIds = prefs.favorites
        if (favIds.isNotEmpty()) {
            val favs = playlist.allItems.filter { favIds.contains(it.id) }
            if (favs.isNotEmpty()) {
                rowsAdapter.add(row(getString(R.string.row_favorites), favs, cardPresenter))
            }
        }

        // Live TV categories.
        val liveCategories = playlist.liveCategories.ifEmpty {
            listOf(Category("all", getString(R.string.row_all_channels)))
        }
        for (category in liveCategories) {
            val channels = if (playlist.liveCategories.isEmpty()) {
                playlist.liveChannels
            } else {
                playlist.channelsFor(category.id)
            }
            if (channels.isNotEmpty()) {
                rowsAdapter.add(row(category.title, channels, cardPresenter))
            }
        }

        // VOD / movies.
        for (section in playlist.vodSections) {
            rowsAdapter.add(row("🎬 ${section.category.title}", section.items, cardPresenter))
        }

        // System actions.
        val menuAdapter = ArrayObjectAdapter(MenuItemPresenter()).apply {
            add(MenuItem(MenuItem.ID_REFRESH, getString(R.string.action_refresh)))
            add(MenuItem(MenuItem.ID_SETTINGS, getString(R.string.action_settings)))
        }
        rowsAdapter.add(ListRow(HeaderItem(getString(R.string.row_system)), menuAdapter))
        lastRenderSignature = renderSignature()
    }

    private fun row(title: String, items: List<Channel>, presenter: CardPresenter): ListRow {
        val adapter = ArrayObjectAdapter(presenter)
        items.forEach { adapter.add(it) }
        return ListRow(HeaderItem(title), adapter)
    }

    private fun onMenuItem(item: MenuItem) {
        when (item.id) {
            MenuItem.ID_SETTINGS -> openSettings()
            MenuItem.ID_REFRESH -> {
                AppRepository.playlist = null
                loadContent(configSignature())
            }
        }
    }

    /** Resolves the stream URL and jumps straight into playback. */
    private fun playChannel(channel: Channel) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val url = AppRepository.get(requireContext()).resolvePlayUrl(channel)
                startActivity(PlaybackActivity.intent(requireContext(), url, channel.name, channel.id))
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
