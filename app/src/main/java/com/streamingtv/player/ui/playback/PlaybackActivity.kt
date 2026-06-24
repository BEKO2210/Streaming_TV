package com.streamingtv.player.ui.playback

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.streamingtv.player.R
import com.streamingtv.player.data.AppRepository
import com.streamingtv.player.data.Channel
import com.streamingtv.player.databinding.ActivityPlaybackBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Full-screen live/VOD playback with 4K track selection and remote zapping. */
class PlaybackActivity : FragmentActivity() {

    private lateinit var binding: ActivityPlaybackBinding
    private var player: ExoPlayer? = null

    private var channelId: String? = null
    private var siblings: List<Channel> = emptyList()
    private var currentIndex: Int = -1

    // Current playback state, kept across stop/start so zapping survives.
    private var currentUrl: String? = null
    private var currentTitle: String = ""
    private var zapJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlaybackBinding.inflate(layoutInflater)
        setContentView(binding.root)

        channelId = intent.getStringExtra(EXTRA_CHANNEL_ID)
        currentUrl = intent.getStringExtra(EXTRA_URL)
        currentTitle = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        buildSiblings()
    }

    override fun onStart() {
        super.onStart()
        val url = currentUrl
        if (url.isNullOrBlank()) {
            Toast.makeText(this, getString(R.string.toast_play_failed), Toast.LENGTH_LONG).show()
            finish()
            return
        }
        initPlayer()
        playUrl(url, currentTitle)
    }

    private fun buildSiblings() {
        val id = channelId ?: return
        val playlist = AppRepository.playlist ?: return
        val current = playlist.liveChannels.firstOrNull { it.id == id } ?: return
        siblings = playlist.liveChannels.filter { it.categoryId == current.categoryId }
        currentIndex = siblings.indexOfFirst { it.id == id }
    }

    private fun initPlayer() {
        // Allow the renderer to pick the highest available quality (up to 4K).
        val trackSelector = DefaultTrackSelector(this).apply {
            parameters = buildUponParameters()
                .clearVideoSizeConstraints()
                .setExceedRendererCapabilitiesIfNecessary(true)
                .build()
        }
        val exo = ExoPlayer.Builder(this)
            .setTrackSelector(trackSelector)
            .build()
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
    }

    private fun playUrl(url: String, title: String) {
        val exo = player ?: return
        val item = MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
            .build()
        exo.setMediaItem(item)
        exo.playWhenReady = true
        exo.prepare()
        currentUrl = url
        currentTitle = title
        binding.textChannelName.text = title
    }

    /** Zaps to a neighbouring live channel; direction +1/-1. */
    private fun zap(direction: Int) {
        if (siblings.isEmpty() || currentIndex < 0) return
        val nextIndex = (currentIndex + direction + siblings.size) % siblings.size
        val next = siblings[nextIndex]
        currentIndex = nextIndex
        channelId = next.id
        // Update the label immediately; cancel any in-flight resolve so rapid
        // zapping doesn't pile up requests or land out of order.
        binding.textChannelName.text = next.name
        zapJob?.cancel()
        zapJob = lifecycleScope.launch {
            try {
                val url = AppRepository.get(this@PlaybackActivity).resolvePlayUrl(next)
                playUrl(url, next.name)
            } catch (e: Exception) {
                Toast.makeText(
                    this@PlaybackActivity,
                    e.message ?: getString(R.string.toast_play_failed),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_MEDIA_NEXT -> {
                zap(+1); return true
            }
            KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                zap(-1); return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_SPACE -> {
                player?.let { it.playWhenReady = !it.playWhenReady }; return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onStop() {
        super.onStop()
        player?.release()
        player = null
    }

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_CHANNEL_ID = "extra_channel_id"

        fun intent(context: Context, url: String, title: String, channelId: String? = null): Intent =
            Intent(context, PlaybackActivity::class.java).apply {
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_CHANNEL_ID, channelId)
            }
    }
}
