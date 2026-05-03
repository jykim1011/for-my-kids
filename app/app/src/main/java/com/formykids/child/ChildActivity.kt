package com.formykids.child

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.formykids.R
import com.formykids.databinding.ActivityChildBinding
import com.formykids.settings.SettingsActivity

class ChildActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChildBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChildBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        } else {
            startAudioService()
        }

        binding.btnSettings?.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        AudioStreamService.limitCallback = {
            runOnUiThread {
                startActivity(Intent(this, com.formykids.billing.PaywallActivity::class.java))
            }
        }
        AudioStreamService.connectionCallback = { connected ->
            runOnUiThread {
                binding.tvServerStatus.text = getString(
                    if (connected) R.string.status_connected else R.string.status_disconnected
                )
            }
        }
        AudioStreamService.statusCallback = { isStreaming ->
            runOnUiThread {
                binding.tvStreamStatus.text =
                    if (isStreaming) getString(R.string.streaming) else getString(R.string.idle)
            }
        }
    }

    private fun startAudioService() {
        val intent = Intent(this, AudioStreamService::class.java)
        ContextCompat.startForegroundService(this, intent)
        val detectionIntent = Intent(this, DangerDetectionService::class.java)
        ContextCompat.startForegroundService(this, detectionIntent)
    }

    override fun onRequestPermissionsResult(rc: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(rc, perms, results)
        if (results.isNotEmpty() && results[0] == PackageManager.PERMISSION_GRANTED) {
            startAudioService()
        }
    }
}
