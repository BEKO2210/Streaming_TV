package com.streamingtv.player.ui.details

import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import coil.load
import com.streamingtv.player.R
import com.streamingtv.player.data.AppRepository
import com.streamingtv.player.data.Channel
import com.streamingtv.player.data.Prefs
import com.streamingtv.player.databinding.ActivityDetailsBinding
import com.streamingtv.player.ui.playback.PlaybackActivity
import kotlinx.coroutines.launch

/**
 * Shows a single channel/movie with now/next EPG and remote-friendly
 * Play / Favorite actions.
 */
class DetailsActivity : FragmentActivity() {

    private lateinit var binding: ActivityDetailsBinding
    private lateinit var prefs: Prefs
    private var channel: Channel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = Prefs(this)

        val id = intent.getStringExtra(EXTRA_CHANNEL_ID)
        val ch = id?.let { AppRepository.findById(it) }
        if (ch == null) {
            Toast.makeText(this, getString(R.string.toast_load_failed), Toast.LENGTH_LONG).show()
            finish()
            return
        }
        channel = ch
        bind(ch)
        loadEpg(ch)

        binding.buttonPlay.requestFocus()
        binding.buttonPlay.setOnClickListener { play(ch) }
        binding.buttonFavorite.setOnClickListener {
            prefs.toggleFavorite(ch.id)
            updateFavoriteLabel(ch)
        }
    }

    private fun bind(ch: Channel) {
        binding.detailsTitle.text = ch.name
        binding.detailsDescription.text = ch.description.orEmpty()
        if (!ch.logoUrl.isNullOrBlank()) {
            binding.detailsLogo.load(ch.logoUrl) {
                placeholder(R.drawable.ic_tv_placeholder)
                error(R.drawable.ic_tv_placeholder)
            }
        }
        updateFavoriteLabel(ch)
    }

    private fun updateFavoriteLabel(ch: Channel) {
        binding.buttonFavorite.text = getString(
            if (prefs.isFavorite(ch.id)) R.string.action_remove_favorite
            else R.string.action_add_favorite
        )
    }

    private fun loadEpg(ch: Channel) {
        lifecycleScope.launch {
            val epg = AppRepository.get(this@DetailsActivity).loadEpg(ch)
            if (epg.isNotEmpty()) {
                binding.detailsNow.text = getString(R.string.epg_now, epg[0].title)
                epg.getOrNull(1)?.let {
                    binding.detailsNext.text = getString(R.string.epg_next, it.title)
                }
                if (binding.detailsDescription.text.isNullOrBlank()) {
                    binding.detailsDescription.text = epg[0].description.orEmpty()
                }
            }
        }
    }

    private fun play(ch: Channel) {
        lifecycleScope.launch {
            try {
                val url = AppRepository.get(this@DetailsActivity).resolvePlayUrl(ch)
                startActivity(PlaybackActivity.intent(this@DetailsActivity, url, ch.name, ch.id))
            } catch (e: Exception) {
                Toast.makeText(
                    this@DetailsActivity,
                    e.message ?: getString(R.string.toast_play_failed),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    companion object {
        const val EXTRA_CHANNEL_ID = "extra_channel_id"
    }
}
