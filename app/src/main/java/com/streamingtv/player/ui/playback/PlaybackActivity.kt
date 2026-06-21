package com.streamingtv.player.ui.playback

import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.streamingtv.player.R
import com.streamingtv.player.databinding.ActivityPlaybackBinding

/** Full-screen live playback for a resolved stream URL. */
class PlaybackActivity : FragmentActivity() {

    private lateinit var binding: ActivityPlaybackBinding
    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlaybackBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    override fun onStart() {
        super.onStart()
        val url = intent.getStringExtra(EXTRA_URL)
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        if (url.isNullOrBlank()) {
            Toast.makeText(this, getString(R.string.toast_play_failed), Toast.LENGTH_LONG).show()
            finish()
            return
        }
        initPlayer(url, title)
    }

    private fun initPlayer(url: String, title: String) {
        val exo = ExoPlayer.Builder(this).build()
        player = exo
        binding.playerView.player = exo
        binding.playerView.keepScreenOn = true

        exo.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(
                    this@PlaybackActivity,
                    getString(R.string.toast_stream_error, error.errorCodeName),
                    Toast.LENGTH_LONG
                ).show()
            }
        })

        val item = MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder().setTitle(title).build()
            )
            .build()
        exo.setMediaItem(item)
        exo.playWhenReady = true
        exo.prepare()
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }

    private fun releasePlayer() {
        player?.release()
        player = null
    }

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_TITLE = "extra_title"
    }
}
