package com.streamingtv.player.ui

import android.animation.ObjectAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.leanback.widget.Presenter
import coil.load
import com.streamingtv.player.R
import com.streamingtv.player.data.Channel
import com.streamingtv.player.data.Prefs

/** Renders a [Channel] as a premium, rounded, focus-elevating card. */
class CardPresenter(private val prefs: Prefs) : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.card_channel, parent, false) as CardView
        view.foreground = ContextCompat.getDrawable(parent.context, R.drawable.card_focus_frame)
        view.setOnFocusChangeListener { v, hasFocus ->
            // Lift the card on focus for an Apple-TV-like sense of depth.
            val z = if (hasFocus) 22f else 0f
            ObjectAnimator.ofFloat(v as CardView, "cardElevation", v.cardElevation, z)
                .setDuration(150).start()
        }
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
        val channel = item as Channel
        val card = viewHolder.view as CardView
        val title = card.findViewById<TextView>(R.id.card_title)
        val subtitle = card.findViewById<TextView>(R.id.card_subtitle)
        val image = card.findViewById<ImageView>(R.id.card_image)
        val badge = card.findViewById<TextView>(R.id.card_badge)

        val star = if (prefs.isFavorite(channel.id)) "★ " else ""
        title.text = "$star${channel.name}"
        subtitle.text = when {
            channel.isVod -> card.context.getString(R.string.label_movie)
            channel.number != null -> "Nr. ${channel.number}"
            else -> ""
        }
        subtitle.visibility = if (subtitle.text.isNullOrBlank()) View.GONE else View.VISIBLE

        if (channel.isVod) {
            badge.visibility = View.VISIBLE
            badge.text = card.context.getString(R.string.badge_vod)
        } else {
            badge.visibility = View.GONE
        }

        val placeholder = InitialsDrawable(channel.name)
        if (!channel.logoUrl.isNullOrBlank()) {
            image.load(channel.logoUrl) {
                placeholder(placeholder)
                error(placeholder)
            }
        } else {
            image.setImageDrawable(placeholder)
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        viewHolder.view.findViewById<ImageView>(R.id.card_image).setImageDrawable(null)
    }
}
