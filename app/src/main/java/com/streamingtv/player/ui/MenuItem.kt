package com.streamingtv.player.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.leanback.widget.Presenter
import com.streamingtv.player.R

/** A simple action card shown in the browse grid (Settings, Refresh, …). */
data class MenuItem(val id: String, val title: String) {
    companion object {
        const val ID_SETTINGS = "settings"
        const val ID_REFRESH = "refresh"
    }
}

/** Presents a [MenuItem] as a focusable text card. */
class MenuItemPresenter : Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_menu_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
        (viewHolder.view as TextView).text = (item as MenuItem).title
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) = Unit
}
