package com.streamingtv.player.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.streamingtv.player.R
import com.streamingtv.player.data.Prefs
import com.streamingtv.player.data.SourceType
import com.streamingtv.player.databinding.ActivitySettingsBinding

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
        Toast.makeText(this, getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()
        finish()
    }
}
