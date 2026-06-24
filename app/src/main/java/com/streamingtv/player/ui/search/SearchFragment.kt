package com.streamingtv.player.ui.search

import android.os.Bundle
import android.widget.Toast
import androidx.leanback.app.SearchSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.ObjectAdapter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.lifecycle.lifecycleScope
import com.streamingtv.player.R
import com.streamingtv.player.data.AppRepository
import com.streamingtv.player.data.Channel
import com.streamingtv.player.data.Prefs
import com.streamingtv.player.ui.CardPresenter
import com.streamingtv.player.ui.playback.PlaybackActivity
import kotlinx.coroutines.launch

/** Searches loaded channels/movies by name. */
class SearchFragment : SearchSupportFragment(), SearchSupportFragment.SearchResultProvider {

    private val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSearchResultProvider(this)
        setOnItemViewClickedListener(OnItemViewClickedListener { _, item, _, _ ->
            if (item is Channel) playChannel(item)
        })
    }

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

    override fun getResultsAdapter(): ObjectAdapter = rowsAdapter

    override fun onQueryTextChange(newQuery: String): Boolean {
        search(newQuery)
        return true
    }

    override fun onQueryTextSubmit(query: String): Boolean {
        search(query)
        return true
    }

    private fun search(query: String) {
        rowsAdapter.clear()
        val q = query.trim()
        if (q.length < 2) return
        val all = AppRepository.playlist?.allItems ?: return
        val matches = all.filter { it.name.contains(q, ignoreCase = true) }.take(60)
        if (matches.isEmpty()) return
        val cardPresenter = CardPresenter(Prefs(requireContext()))
        val listAdapter = ArrayObjectAdapter(cardPresenter)
        matches.forEach { listAdapter.add(it) }
        val header = HeaderItem(getString(R.string.search_results, matches.size))
        rowsAdapter.add(ListRow(header, listAdapter))
    }
}
