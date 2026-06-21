package com.streamingtv.player.ui

import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import coil.load
import com.streamingtv.player.R
import com.streamingtv.player.data.Channel

/** Renders a [Channel] as a focusable Leanback image card. */
class CardPresenter : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val cardView = ImageCardView(parent.context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setMainImageScaleType(ImageView.ScaleType.FIT_CENTER)
            setMainImageDimensions(CARD_WIDTH, CARD_HEIGHT)
        }
        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
        val channel = item as Channel
        val card = viewHolder.view as ImageCardView
        card.titleText = channel.name
        card.contentText = channel.number?.let { "Nr. $it" } ?: ""
        val placeholder = ContextCompat.getDrawable(card.context, R.drawable.ic_tv_placeholder)
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
        private const val CARD_WIDTH = 280
        private const val CARD_HEIGHT = 180
    }
}
