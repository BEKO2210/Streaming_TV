package com.streamingtv.player.ui.playback

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.leanback.widget.BaseGridView
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import coil.load
import com.streamingtv.player.R
import com.streamingtv.player.data.AppRepository
import com.streamingtv.player.data.Channel
import com.streamingtv.player.data.EpgProgram
import com.streamingtv.player.data.Prefs
import com.streamingtv.player.databinding.ActivityPlaybackBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Full-screen live/VOD player with a TiviMate-style HUD: zap info bar with
 * now/next EPG and progress, an in-player channel list with group switching,
 * an options panel (aspect ratio, video/audio/subtitle tracks, favorite),
 * number-key channel jump and automatic reconnect on stream drops. 4K-ready
 * via an unconstrained track selector.
 */
class PlaybackActivity : FragmentActivity() {

    private lateinit var binding: ActivityPlaybackBinding
    private lateinit var trackSelector: DefaultTrackSelector
    private lateinit var prefs: Prefs
    private var player: ExoPlayer? = null

    /** A selectable group in the player: All, Favorites, or a category. */
    private data class Group(val id: String?, val name: String, val channels: List<Channel>)

    private var groups: List<Group> = emptyList()
    private var groupIndex = 0
    private var currentIndex = -1

    private var current: Channel? = null
    private var currentUrl: String? = null
    private var previousChannel: Channel? = null

    private var zapJob: Job? = null
    private var epgJob: Job? = null
    private var previewJob: Job? = null
    private var currentEpg: List<EpgProgram> = emptyList()
    private var pendingPreview: Channel? = null

    private var retries = 0
    private var aspectIndex = 0
    private val numberInput = StringBuilder()

    private val handler = Handler(Looper.getMainLooper())
    private val clock = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val clockSec = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private enum class Panel { NONE, CHANNELS, SETTINGS }
    private var panel = Panel.NONE

    private lateinit var channelAdapter: ChannelListAdapter
    private val optionAdapter = OptionAdapter(emptyList())

    private val hideInfoRunnable = Runnable { binding.infoBar.visibility = View.GONE }
    private val previewRunnable = Runnable { runPreview() }
    private val commitNumberRunnable = Runnable { commitNumberJump() }
    private val tickRunnable = object : Runnable {
        override fun run() {
            updateClockAndProgress()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlaybackBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = Prefs(this)

        val id = intent.getStringExtra(EXTRA_CHANNEL_ID)
        currentUrl = intent.getStringExtra(EXTRA_URL)
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        current = id?.let { AppRepository.findById(it) }
            ?: Channel(id = id.orEmpty(), name = title, streamUrl = currentUrl.orEmpty())

        buildGroups(preserveId = current?.categoryId)
        currentIndex = activeChannels().indexOfFirst { it.id == current?.id }
        setupChannelList()
        binding.panelOptionList.adapter = optionAdapter
        binding.panelOptionList.windowAlignment = BaseGridView.WINDOW_ALIGN_NO_EDGE
        binding.panelOptionList.windowAlignmentOffsetPercent = 33f
        binding.panelOptionList.itemAlignmentOffsetPercent = 0f
        // Keep D-pad focus inside the options panel (swallow horizontal moves).
        binding.panelOptionList.setOnKeyInterceptListener { event ->
            event.action == KeyEvent.ACTION_DOWN &&
                (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT || event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)
        }
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
        playUrl(url, current?.name.orEmpty())
        current?.id?.let { prefs.addRecent(it) }
        showInfoBar()
        loadEpg()
        handler.post(tickRunnable)
    }

    // ---------------------------------------------------------------- groups

    private fun buildGroups(preserveId: String?) {
        val playlist = AppRepository.playlist
        val list = mutableListOf<Group>()
        if (playlist != null && playlist.liveChannels.isNotEmpty()) {
            list.add(Group(null, getString(R.string.group_all), playlist.liveChannels))
            val favs = playlist.liveChannels.filter { prefs.favorites.contains(it.id) }
            if (favs.isNotEmpty()) list.add(Group(GROUP_FAV, getString(R.string.group_favorites), favs))
            playlist.liveCategories.forEach { cat ->
                val chans = playlist.liveChannels.filter { it.categoryId == cat.id }
                if (chans.isNotEmpty()) list.add(Group(cat.id, cat.title, chans))
            }
        }
        groups = list
        groupIndex = groups.indexOfFirst { it.id == preserveId }.takeIf { it >= 0 } ?: 0
    }

    private fun activeChannels(): List<Channel> = groups.getOrNull(groupIndex)?.channels ?: emptyList()

    private fun setupChannelList() {
        channelAdapter = ChannelListAdapter(
            items = activeChannels(),
            favorites = prefs.favorites,
            onClick = { ch -> switchTo(ch) },
            onFocus = { ch -> previewEpg(ch) }
        )
        channelAdapter.setCurrent(current?.id)
        binding.panelChannelList.adapter = channelAdapter
        // Keep the focused row ~1/3 down so the list scrolls smoothly instead of
        // pinning the selection to the panel edge.
        binding.panelChannelList.windowAlignment = BaseGridView.WINDOW_ALIGN_NO_EDGE
        binding.panelChannelList.windowAlignmentOffsetPercent = 33f
        binding.panelChannelList.itemAlignmentOffsetPercent = 0f
        binding.panelChannelList.setOnKeyInterceptListener { event ->
            if (event.action == KeyEvent.ACTION_DOWN) when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> { cycleGroup(-1); true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { cycleGroup(+1); true }
                else -> false
            } else false
        }
    }

    private fun cycleGroup(direction: Int) {
        if (groups.size <= 1) return
        groupIndex = (groupIndex + direction + groups.size) % groups.size
        refreshChannelPanel()
    }

    private fun refreshChannelPanel() {
        val group = groups.getOrNull(groupIndex) ?: return
        binding.panelCategory.text = getString(R.string.group_header, group.name)
        channelAdapter.submit(group.channels, prefs.favorites, current?.id)
        val max = (group.channels.size - 1).coerceAtLeast(0)
        val sel = group.channels.indexOfFirst { it.id == current?.id }.coerceIn(0, max)
        // Wait for the freshly submitted items to lay out before selecting/focusing.
        binding.panelChannelList.post {
            binding.panelChannelList.selectedPosition = sel
            if (!binding.panelChannelList.requestFocus()) {
                binding.panelChannelList.post { binding.panelChannelList.requestFocus() }
            }
        }
    }

    // -------------------------------------------------------------- player

    private fun initPlayer() {
        trackSelector = DefaultTrackSelector(this).apply {
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
        applyAspect()

        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                binding.buffering.visibility =
                    if (state == Player.STATE_BUFFERING) View.VISIBLE else View.GONE
                if (state == Player.STATE_READY) retries = 0
            }

            override fun onTracksChanged(tracks: Tracks) {
                // Tracks are empty until READY; refresh an open options panel.
                if (panel == Panel.SETTINGS) refreshOptions()
            }

            override fun onPlayerError(error: PlaybackException) {
                if (retries < MAX_RETRIES) {
                    retries++
                    binding.buffering.visibility = View.VISIBLE
                    handler.postDelayed({ player?.prepare() }, (1500L * retries).coerceAtMost(8000L))
                } else {
                    Toast.makeText(
                        this@PlaybackActivity,
                        getString(R.string.toast_stream_error, error.errorCodeName),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        })
    }

    private fun playUrl(url: String, title: String) {
        val exo = player ?: return
        retries = 0
        val item = MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
            .build()
        exo.setMediaItem(item)
        exo.playWhenReady = true
        exo.prepare()
        currentUrl = url
    }

    /** Switches to [channel] across zap, channel list and number jump. */
    private fun switchTo(channel: Channel) {
        closePanels()
        if (channel.id == current?.id) { showInfoBar(); return }
        previousChannel = current
        current = channel
        // Keep the active group coherent so up/down zapping stays predictable.
        if (activeChannels().none { it.id == channel.id }) {
            groupIndex = groups.indexOfFirst { it.id == channel.categoryId }
                .takeIf { it >= 0 } ?: 0
        }
        currentIndex = activeChannels().indexOfFirst { it.id == channel.id }
        channelAdapter.setCurrent(channel.id)
        prefs.addRecent(channel.id)
        showInfoBar()
        zapJob?.cancel()
        zapJob = lifecycleScope.launch {
            try {
                val url = AppRepository.get(this@PlaybackActivity).resolvePlayUrl(channel)
                playUrl(url, channel.name)
                loadEpg()
            } catch (e: Exception) {
                Toast.makeText(
                    this@PlaybackActivity,
                    e.message ?: getString(R.string.toast_play_failed),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun zap(direction: Int) {
        val chans = activeChannels()
        if (chans.isEmpty()) return
        val base = if (currentIndex in chans.indices) currentIndex else 0
        switchTo(chans[(base + direction + chans.size) % chans.size])
    }

    private fun gotoPrevious() = previousChannel?.let { switchTo(it) } ?: Unit

    // ------------------------------------------------------------------ EPG

    /** Returns the (now, next) program pair, skipping already-finished entries. */
    private fun nowNext(epg: List<EpgProgram>): Pair<EpgProgram?, EpgProgram?> {
        if (epg.isEmpty()) return null to null
        val t = System.currentTimeMillis()
        val i = epg.indexOfFirst { p ->
            val s = p.startMs; val e = p.endMs
            s != null && e != null && t >= s && t < e
        }
        val idx = if (i >= 0) i else 0
        return epg.getOrNull(idx) to epg.getOrNull(idx + 1)
    }

    private fun loadEpg() {
        val ch = current ?: return
        currentEpg = emptyList()
        epgJob?.cancel()
        epgJob = lifecycleScope.launch {
            currentEpg = AppRepository.get(this@PlaybackActivity).loadEpg(ch)
            renderEpg()
        }
    }

    private fun renderEpg() {
        val (now, next) = nowNext(currentEpg)
        binding.infoNow.text = now?.let { getString(R.string.epg_now, timeRange(it) + it.title) }.orEmpty()
        binding.infoNext.text = next?.let { getString(R.string.epg_next, it.title) }.orEmpty()
        updateClockAndProgress()
    }

    private fun previewEpg(channel: Channel) {
        pendingPreview = channel
        binding.panelPreviewNow.text = channel.name
        binding.panelPreviewNext.text = ""
        handler.removeCallbacks(previewRunnable)
        handler.postDelayed(previewRunnable, PREVIEW_DEBOUNCE)
    }

    private fun runPreview() {
        val channel = pendingPreview ?: return
        previewJob?.cancel()
        previewJob = lifecycleScope.launch {
            val epg = AppRepository.get(this@PlaybackActivity).loadEpg(channel)
            if (pendingPreview?.id != channel.id) return@launch
            val (now, next) = nowNext(epg)
            binding.panelPreviewNow.text = now?.let { timeRange(it) + it.title } ?: channel.name
            binding.panelPreviewNext.text = next?.let { "▶ ${it.title}" }.orEmpty()
        }
    }

    private fun timeRange(p: EpgProgram): String {
        val s = p.startMs ?: return ""
        val e = p.endMs ?: return ""
        return "${clock.format(Date(s))}–${clock.format(Date(e))}  "
    }

    private fun updateClockAndProgress() {
        binding.infoClock.text = clockSec.format(Date(System.currentTimeMillis()))
        val (now, _) = nowNext(currentEpg)
        val progress = now?.progressAt(System.currentTimeMillis())
        if (progress != null) {
            binding.infoProgress.visibility = View.VISIBLE
            binding.infoProgress.progress = (progress * 100).toInt()
        } else {
            binding.infoProgress.visibility = View.INVISIBLE
        }
    }

    // -------------------------------------------------------------- info bar

    private fun bindInfoStatic(channel: Channel) {
        binding.infoNumber.text = channel.number
            ?: ((currentIndex + 1).takeIf { it > 0 }?.toString() ?: "")
        binding.infoName.text = channel.name
        if (!channel.logoUrl.isNullOrBlank()) {
            binding.infoLogo.load(channel.logoUrl) {
                placeholder(R.drawable.ic_tv_placeholder)
                error(R.drawable.ic_tv_placeholder)
            }
        } else {
            binding.infoLogo.setImageResource(R.drawable.ic_tv_placeholder)
        }
        if (channel.isVod) {
            binding.infoBadge.visibility = View.VISIBLE
            binding.infoBadge.text = getString(R.string.label_movie)
        } else {
            binding.infoBadge.visibility = View.GONE
        }
    }

    private fun showInfoBar() {
        current?.let { bindInfoStatic(it) }
        renderEpg()
        binding.infoBar.visibility = View.VISIBLE
        handler.removeCallbacks(hideInfoRunnable)
        handler.postDelayed(hideInfoRunnable, INFO_TIMEOUT)
    }

    private fun hideInfoBar() {
        binding.infoBar.visibility = View.GONE
        handler.removeCallbacks(hideInfoRunnable)
    }

    // --------------------------------------------------------------- panels

    private fun openChannels() {
        if (activeChannels().isEmpty()) {
            groupIndex = 0 // fall back to "All"
            if (activeChannels().isEmpty()) {
                Toast.makeText(this, getString(R.string.toast_no_channels), Toast.LENGTH_SHORT).show()
                return
            }
        }
        hideInfoBar()
        binding.panelSettings.visibility = View.GONE
        binding.panelChannels.visibility = View.VISIBLE
        panel = Panel.CHANNELS
        refreshChannelPanel()
    }

    private fun openSettings() {
        hideInfoBar()
        binding.panelChannels.visibility = View.GONE
        optionAdapter.submit(buildOptions())
        binding.panelSettings.visibility = View.VISIBLE
        panel = Panel.SETTINGS
        binding.panelOptionList.post {
            binding.panelOptionList.selectedPosition = 0
            binding.panelOptionList.requestFocus()
        }
    }

    private fun closePanels() {
        binding.panelChannels.visibility = View.GONE
        binding.panelSettings.visibility = View.GONE
        panel = Panel.NONE
    }

    // --------------------------------------------------------- options panel

    /** Rebuilds the options list while preserving the focused row. */
    private fun refreshOptions() {
        val pos = binding.panelOptionList.selectedPosition
        optionAdapter.submit(buildOptions())
        binding.panelOptionList.post {
            val max = (optionAdapter.itemCount - 1).coerceAtLeast(0)
            binding.panelOptionList.selectedPosition = pos.coerceIn(0, max)
            binding.panelOptionList.requestFocus()
        }
    }

    private fun buildOptions(): List<OptionRow> {
        val rows = mutableListOf<OptionRow>()
        val ch = current

        if (ch != null && !ch.isVod) {
            val isFav = prefs.isFavorite(ch.id)
            rows.add(
                OptionRow(
                    title = getString(if (isFav) R.string.opt_favorite_remove else R.string.opt_favorite_add),
                    selected = isFav,
                    onClick = { toggleFavorite() }
                )
            )
        }

        rows.add(
            OptionRow(
                title = getString(R.string.opt_aspect),
                subtitle = getString(ASPECT_MODES[aspectIndex].second),
                onClick = {
                    aspectIndex = (aspectIndex + 1) % ASPECT_MODES.size
                    applyAspect()
                    refreshOptions()
                }
            )
        )

        val tracks = player?.currentTracks
        addTrackRows(rows, tracks, C.TRACK_TYPE_VIDEO, R.string.opt_video, withAuto = true)
        addTrackRows(rows, tracks, C.TRACK_TYPE_AUDIO, R.string.opt_audio, withAuto = false)
        addSubtitleRows(rows, tracks)

        player?.videoFormat?.let { f ->
            if (f.width > 0 && f.height > 0) {
                rows.add(OptionRow(title = getString(R.string.opt_stream_info, f.width, f.height), onClick = {}))
            }
        }
        return rows
    }

    private fun addTrackRows(
        rows: MutableList<OptionRow>,
        tracks: Tracks?,
        type: Int,
        labelRes: Int,
        withAuto: Boolean
    ) {
        val groupsOfType = tracks?.groups?.filter { it.type == type && it.isSupported }.orEmpty()
        if (groupsOfType.isEmpty()) return
        if (withAuto) {
            val hasOverride = trackSelector.parameters.overrides.values.any { it.type == type }
            rows.add(
                OptionRow(
                    title = getString(labelRes, getString(R.string.opt_auto)),
                    selected = !hasOverride,
                    onClick = { clearTrackOverride(type); refreshOptions() }
                )
            )
        }
        groupsOfType.forEachIndexed { gi, group ->
            for (ti in 0 until group.length) {
                val format = group.getTrackFormat(ti)
                rows.add(
                    OptionRow(
                        title = getString(labelRes, trackLabel(format.language, format.label, format.width, format.height, gi, ti)),
                        selected = group.isTrackSelected(ti),
                        onClick = { selectTrack(group, ti); refreshOptions() }
                    )
                )
            }
        }
    }

    private fun addSubtitleRows(rows: MutableList<OptionRow>, tracks: Tracks?) {
        val textGroups = tracks?.groups?.filter { it.type == C.TRACK_TYPE_TEXT && it.isSupported }.orEmpty()
        if (textGroups.isEmpty()) return
        rows.add(
            OptionRow(
                title = getString(R.string.opt_subtitle, getString(R.string.opt_off)),
                selected = isTextDisabled(),
                onClick = { disableText(); refreshOptions() }
            )
        )
        textGroups.forEachIndexed { gi, group ->
            for (ti in 0 until group.length) {
                val format = group.getTrackFormat(ti)
                rows.add(
                    OptionRow(
                        title = getString(R.string.opt_subtitle, trackLabel(format.language, format.label, 0, 0, gi, ti)),
                        selected = group.isTrackSelected(ti) && !isTextDisabled(),
                        onClick = { selectTrack(group, ti); refreshOptions() }
                    )
                )
            }
        }
    }

    private fun trackLabel(language: String?, label: String?, w: Int, h: Int, gi: Int, ti: Int): String =
        label ?: when {
            w > 0 && h > 0 -> "${h}p"
            !language.isNullOrBlank() -> language
            else -> "Spur ${gi + 1}.${ti + 1}"
        }

    private fun selectTrack(group: Tracks.Group, trackIndex: Int) {
        trackSelector.parameters = trackSelector.buildUponParameters()
            .setTrackTypeDisabled(group.type, false)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, listOf(trackIndex)))
            .build()
    }

    private fun clearTrackOverride(type: Int) {
        trackSelector.parameters = trackSelector.buildUponParameters()
            .clearOverridesOfType(type)
            .setTrackTypeDisabled(type, false)
            .build()
    }

    private fun disableText() {
        trackSelector.parameters = trackSelector.buildUponParameters()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
    }

    private fun isTextDisabled(): Boolean =
        trackSelector.parameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)

    private fun applyAspect() {
        binding.playerView.resizeMode = ASPECT_MODES[aspectIndex].first
    }

    private fun toggleFavorite() {
        val ch = current ?: return
        val added = prefs.toggleFavorite(ch.id)
        Toast.makeText(
            this,
            getString(if (added) R.string.toast_favorite_added else R.string.toast_favorite_removed, ch.name),
            Toast.LENGTH_SHORT
        ).show()
        // Rebuild groups so the Favorites group reflects the change.
        val keepId = groups.getOrNull(groupIndex)?.id
        buildGroups(preserveId = keepId)
        currentIndex = activeChannels().indexOfFirst { it.id == current?.id }
        if (panel == Panel.CHANNELS) refreshChannelPanel()
        if (panel == Panel.SETTINGS) refreshOptions()
    }

    // -------------------------------------------------------- number jump

    private fun onDigit(digit: Int) {
        if (numberInput.length >= 4) numberInput.clear()
        numberInput.append(digit)
        binding.numberOverlay.visibility = View.VISIBLE
        binding.numberOverlay.text = numberInput
        handler.removeCallbacks(commitNumberRunnable)
        handler.postDelayed(commitNumberRunnable, NUMBER_TIMEOUT)
    }

    private fun commitNumberJump() {
        val wanted = numberInput.toString()
        numberInput.clear()
        binding.numberOverlay.visibility = View.GONE
        if (wanted.isBlank()) return
        val all = AppRepository.playlist?.liveChannels ?: return
        val target = all.firstOrNull { it.number == wanted }
            ?: all.getOrNull((wanted.toIntOrNull() ?: 0) - 1)
        if (target != null) switchTo(target)
        else Toast.makeText(this, getString(R.string.toast_channel_not_found, wanted), Toast.LENGTH_SHORT).show()
    }

    // ------------------------------------------------------------ key input

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
            onDigit(keyCode - KeyEvent.KEYCODE_0)
            return true
        }
        when (keyCode) {
            // Up/down AND left/right all change channel while watching.
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_PREVIOUS ->
                if (panel == Panel.NONE) { zap(-1); return true }
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN,
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_NEXT ->
                if (panel == Panel.NONE) { zap(+1); return true }
            // OK: tap = channel list, hold = options (handled in onKeyUp/LongPress).
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER ->
                if (panel == Panel.NONE) {
                    if (event?.repeatCount == 0) event.startTracking()
                    return true
                }
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_PROG_YELLOW ->
                if (panel == Panel.NONE) { openSettings(); return true }
            KeyEvent.KEYCODE_INFO ->
                if (panel == Panel.NONE) {
                    if (binding.infoBar.visibility == View.VISIBLE) hideInfoBar() else showInfoBar()
                    return true
                }
            KeyEvent.KEYCODE_LAST_CHANNEL, KeyEvent.KEYCODE_PROG_RED ->
                if (panel == Panel.NONE) { gotoPrevious(); return true }
            KeyEvent.KEYCODE_PROG_GREEN ->
                if (panel == Panel.NONE) { toggleFavorite(); return true }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                player?.let { it.playWhenReady = !it.playWhenReady }; return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent?): Boolean {
        if (panel == Panel.NONE &&
            (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)
        ) {
            openSettings()
            return true
        }
        return super.onKeyLongPress(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (panel == Panel.NONE &&
            (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)
        ) {
            // Short tap (long press already opened options and cancels the up).
            if (event != null && event.isTracking && !event.isCanceled) openChannels()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onBackPressed() {
        when {
            panel != Panel.NONE -> closePanels()
            binding.infoBar.visibility == View.VISIBLE -> hideInfoBar()
            else -> super.onBackPressed()
        }
    }

    // ------------------------------------------------------------- lifecycle

    override fun onStop() {
        super.onStop()
        handler.removeCallbacksAndMessages(null)
        zapJob?.cancel(); epgJob?.cancel(); previewJob?.cancel()
        numberInput.clear()
        binding.numberOverlay.visibility = View.GONE
        binding.buffering.visibility = View.GONE
        player?.release()
        player = null
    }

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_CHANNEL_ID = "extra_channel_id"

        private const val GROUP_FAV = "__fav__"
        private const val MAX_RETRIES = 5
        private const val INFO_TIMEOUT = 5000L
        private const val NUMBER_TIMEOUT = 2500L
        private const val PREVIEW_DEBOUNCE = 350L

        private val ASPECT_MODES = listOf(
            AspectRatioFrameLayout.RESIZE_MODE_FIT to R.string.aspect_fit,
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM to R.string.aspect_zoom,
            AspectRatioFrameLayout.RESIZE_MODE_FILL to R.string.aspect_fill,
            AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH to R.string.aspect_width,
            AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT to R.string.aspect_height
        )

        fun intent(context: Context, url: String, title: String, channelId: String? = null): Intent =
            Intent(context, PlaybackActivity::class.java).apply {
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_CHANNEL_ID, channelId)
            }
    }
}
