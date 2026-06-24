package com.streamingtv.player.ui

import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import coil.load
import com.streamingtv.player.R
import com.streamingtv.player.data.Channel
import com.streamingtv.player.data.Prefs

/** Renders a [Channel] as a focusable Leanback image card. */
class CardPresenter(private val prefs: Prefs) : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val cardView = ImageCardView(parent.context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setMainImageScaleType(ImageView.ScaleType.FIT_CENTER)
            setMainImageDimensions(CARD_WIDTH, CARD_HEIGHT)
            setInfoAreaBackgroundColor(
                ContextCompat.getColor(parent.context, R.color.brand_surface)
            )
        }
        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
        val channel = item as Channel
        val card = viewHolder.view as ImageCardView
        val star = if (prefs.isFavorite(channel.id)) "★ " else ""
        card.titleText = "$star${channel.name}"
        card.contentText = when {
            channel.isVod -> card.context.getString(R.string.label_movie)
            channel.number != null -> "Nr. ${channel.number}"
            else -> ""
        }
        val placeholder = InitialsDrawable(channel.name)
        val imageView = card.mainImageView
        if (!channel.logoUrl.isNullOrBlank()) {
            imageView.load(channel.logoUrl) {
                placeholder(placeholder)
                error(placeholder)
            }
        } else {
            imageView.setImageDrawable(placeholder)
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        (viewHolder.view as ImageCardView).mainImage = null
    }

    companion object {
        private const val CARD_WIDTH = 300
        private const val CARD_HEIGHT = 180
    }
}
