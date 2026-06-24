package com.streamingtv.player.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.streamingtv.player.R
import com.streamingtv.player.data.Prefs
import com.streamingtv.player.data.SourceType
import com.streamingtv.player.data.m3u.M3uParser
import com.streamingtv.player.data.stalker.StalkerClient
import com.streamingtv.player.databinding.ActivitySettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Lets the user configure their provider.
 *
 * The MAC address is shown prominently so it can be read out or copied and
 * given to the provider ("Betreiber") to activate the line.
 */
class SettingsActivity : FragmentActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = Prefs(this)

        bindCurrentValues()

        binding.radioStalker.setOnClickListener { updateModeVisibility(SourceType.STALKER_PORTAL) }
        binding.radioM3u.setOnClickListener { updateModeVisibility(SourceType.M3U_PLAYLIST) }

        binding.buttonGenerateMac.setOnClickListener {
            val mac = Prefs.generateMac()
            prefs.macAddress = mac
            binding.inputMac.setText(mac)
            binding.textMacDisplay.text = mac
        }

        binding.buttonCopyMac.setOnClickListener {
            val mac = binding.inputMac.text?.toString()?.trim().orEmpty()
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("MAC", mac))
            Toast.makeText(this, getString(R.string.mac_copied, mac), Toast.LENGTH_SHORT).show()
        }

        binding.inputMac.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) binding.textMacDisplay.text =
                binding.inputMac.text?.toString()?.trim().orEmpty()
        }

        binding.buttonSave.setOnClickListener { save() }
        binding.buttonTest.setOnClickListener { testConnection() }
    }

    /**
     * Validates the entered provider details by actually connecting, so the
     * user knows the line works (and the MAC is activated) before saving.
     */
    private fun testConnection() {
        val stalker = binding.radioStalker.isChecked
        val portal = binding.inputPortal.text?.toString()?.trim().orEmpty()
        val mac = binding.inputMac.text?.toString()?.trim().orEmpty()
        val m3u = binding.inputM3u.text?.toString()?.trim().orEmpty()

        if (stalker && (portal.isBlank() || mac.isBlank())) {
            showResult(getString(R.string.test_need_portal), ok = false); return
        }
        if (!stalker && m3u.isBlank()) {
            showResult(getString(R.string.test_need_m3u), ok = false); return
        }

        binding.testResult.text = getString(R.string.test_running)
        binding.testResult.setTextColor(ContextCompat.getColor(this, R.color.brand_text))
        binding.testProgress.visibility = View.VISIBLE
        binding.buttonTest.isEnabled = false

        lifecycleScope.launch {
            val result = runCatching {
                if (stalker) {
                    val client = StalkerClient(portal, mac)
                    client.connect()
                    client.getChannels().size
                } else {
                    testM3u(m3u)
                }
            }
            binding.testProgress.visibility = View.GONE
            binding.buttonTest.isEnabled = true
            result.onSuccess { count ->
                if (count > 0) showResult(getString(R.string.test_ok, count), ok = true)
                else showResult(getString(R.string.test_ok_empty), ok = true)
            }.onFailure { e ->
                showResult(getString(R.string.test_fail, e.message ?: e.javaClass.simpleName), ok = false)
            }
        }
    }

    private suspend fun testM3u(url: String): Int = withContext(Dispatchers.IO) {
        val http = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder().url(url).header("User-Agent", "StreamingTV/1.0").build()
        val body = http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            resp.body?.string().orEmpty()
        }
        M3uParser.parse(body).channels.size
    }

    private fun showResult(message: String, ok: Boolean) {
        binding.testResult.text = message
        binding.testResult.setTextColor(
            ContextCompat.getColor(this, if (ok) R.color.brand_accent else R.color.brand_text)
        )
    }

    private fun bindCurrentValues() {
        when (prefs.sourceType) {
            SourceType.STALKER_PORTAL -> binding.radioStalker.isChecked = true
            SourceType.M3U_PLAYLIST -> binding.radioM3u.isChecked = true
        }
        binding.inputPortal.setText(prefs.portalUrl)
        // Accessing macAddress generates one on first run if empty.
        val mac = prefs.macAddress
        binding.inputMac.setText(mac)
        binding.textMacDisplay.text = mac
        binding.inputM3u.setText(prefs.m3uUrl)
        updateModeVisibility(prefs.sourceType)
    }

    private fun updateModeVisibility(type: SourceType) {
        val stalker = type == SourceType.STALKER_PORTAL
        binding.groupStalker.visibility = if (stalker) View.VISIBLE else View.GONE
        binding.groupM3u.visibility = if (stalker) View.GONE else View.VISIBLE
    }

    private fun save() {
        val type = if (binding.radioStalker.isChecked) {
            SourceType.STALKER_PORTAL
        } else {
            SourceType.M3U_PLAYLIST
        }
        prefs.sourceType = type
        prefs.portalUrl = binding.inputPortal.text?.toString().orEmpty()
        prefs.macAddress = binding.inputMac.text?.toString().orEmpty()
        prefs.m3uUrl = binding.inputM3u.text?.toString().orEmpty()

        if (!prefs.isConfigured) {
            Toast.makeText(this, getString(R.string.settings_incomplete), Toast.LENGTH_LONG).show()
            return
        }
        // Force a fresh session/catalog next time the browse screen loads.
        com.streamingtv.player.data.AppRepository.reset(this)
        Toast.makeText(this, getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()
        finish()
    }
}
