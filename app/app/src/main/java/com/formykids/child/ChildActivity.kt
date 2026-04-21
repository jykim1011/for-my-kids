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
                // Show paywall - for now just show a toast
                android.widget.Toast.makeText(this, "오늘 무료 이용 시간이 끝났습니다.", android.widget.Toast.LENGTH_LONG).show()
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
                    if (isStreaming) "스트리밍: 전송 중 🎙️" else "스트리밍: 대기 중"
            }
        }
    }

    private fun startAudioService() {
        val intent = Intent(this, AudioStreamService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    override fun onRequestPermissionsResult(rc: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(rc, perms, results)
        if (results.isNotEmpty() && results[0] == PackageManager.PERMISSION_GRANTED) {
            startAudioService()
        }
    }
}
