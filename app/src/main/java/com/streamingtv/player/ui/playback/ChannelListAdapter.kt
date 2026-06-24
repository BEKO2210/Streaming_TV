package com.streamingtv.player.ui.playback

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.streamingtv.player.R
import com.streamingtv.player.data.Channel

/** Channel list shown in the left HUD panel while the stream keeps playing. */
class ChannelListAdapter(
    private var items: List<Channel>,
    private var favorites: Set<String>,
    private val onClick: (Channel) -> Unit,
    private val onFocus: (Channel) -> Unit
) : RecyclerView.Adapter<ChannelListAdapter.VH>() {

    private var currentId: String? = null

    /** Replaces the visible channels (e.g. after switching group). */
    fun submit(newItems: List<Channel>, newFavorites: Set<String>, currentId: String?) {
        items = newItems
        favorites = newFavorites
        this.currentId = currentId
        notifyDataSetChanged()
    }

    fun setCurrent(id: String?) {
        val old = items.indexOfFirst { it.id == currentId }
        currentId = id
        val now = items.indexOfFirst { it.id == id }
        if (old >= 0) notifyItemChanged(old)
        if (now >= 0) notifyItemChanged(now)
    }

    /** Index of the channel currently playing, or 0. */
    fun currentIndex(): Int = items.indexOfFirst { it.id == currentId }.coerceAtLeast(0)

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val number: TextView = view.findViewById(R.id.row_number)
        val logo: ImageView = view.findViewById(R.id.row_logo)
        val name: TextView = view.findViewById(R.id.row_name)
        val fav: TextView = view.findViewById(R.id.row_fav)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_channel_row, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val channel = items[position]
        holder.number.text = channel.number ?: (position + 1).toString()
        holder.name.text = channel.name
        holder.fav.visibility = if (favorites.contains(channel.id)) View.VISIBLE else View.GONE
        holder.itemView.isActivated = channel.id == currentId
        val placeholder = com.streamingtv.player.ui.InitialsDrawable(channel.name)
        if (!channel.logoUrl.isNullOrBlank()) {
            holder.logo.load(channel.logoUrl) {
                placeholder(placeholder)
                error(placeholder)
            }
        } else {
            holder.logo.setImageDrawable(placeholder)
        }
        holder.itemView.setOnClickListener { onClick(channel) }
        holder.itemView.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) onFocus(channel)
        }
    }

    override fun getItemCount(): Int = items.size
}
