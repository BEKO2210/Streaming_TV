package com.streamingtv.player.ui.playback

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.streamingtv.player.R

/** One actionable row in the right-hand options panel. */
data class OptionRow(
    val title: String,
    val subtitle: String? = null,
    val selected: Boolean = false,
    val onClick: () -> Unit
)

/** Generic vertical list for the player options panel. */
class OptionAdapter(private var rows: List<OptionRow>) :
    RecyclerView.Adapter<OptionAdapter.VH>() {

    fun submit(newRows: List<OptionRow>) {
        rows = newRows
        notifyDataSetChanged()
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.option_title)
        val subtitle: TextView = view.findViewById(R.id.option_subtitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_option_row, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val row = rows[position]
        holder.title.text = row.title
        if (row.subtitle.isNullOrBlank()) {
            holder.subtitle.visibility = View.GONE
        } else {
            holder.subtitle.visibility = View.VISIBLE
            holder.subtitle.text = row.subtitle
        }
        holder.itemView.isActivated = row.selected
        holder.itemView.setOnClickListener { row.onClick() }
    }

    override fun getItemCount(): Int = rows.size
}
