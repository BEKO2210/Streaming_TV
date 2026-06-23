package com.streamingtv.player.ui.search

import android.content.Intent
import android.os.Bundle
import androidx.leanback.app.SearchSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.ObjectAdapter
import androidx.leanback.widget.OnItemViewClickedListener
import com.streamingtv.player.R
import com.streamingtv.player.data.AppRepository
import com.streamingtv.player.data.Channel
import com.streamingtv.player.data.Prefs
import com.streamingtv.player.ui.CardPresenter
import com.streamingtv.player.ui.details.DetailsActivity

/** Searches loaded channels/movies by name. */
class SearchFragment : SearchSupportFragment(), SearchSupportFragment.SearchResultProvider {

    private val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSearchResultProvider(this)
        setOnItemViewClickedListener(OnItemViewClickedListener { _, item, _, _ ->
            if (item is Channel) {
                startActivity(Intent(requireContext(), DetailsActivity::class.java).apply {
                    putExtra(DetailsActivity.EXTRA_CHANNEL_ID, item.id)
                })
            }
        })
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
