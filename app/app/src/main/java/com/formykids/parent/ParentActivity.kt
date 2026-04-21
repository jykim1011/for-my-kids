package com.formykids.parent

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.formykids.App
import com.formykids.R
import com.formykids.WebSocketManager
import com.formykids.databinding.ActivityParentBinding
import com.formykids.settings.SettingsActivity
import org.json.JSONObject

class ParentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityParentBinding
    private var player: AudioPlayer? = null
    private var listening = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityParentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWebSocket()

        binding.btnSettings?.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.btnListen.setOnClickListener {
            listening = !listening
            if (listening) {
                player = AudioPlayer()
                WebSocketManager.send("""{"type":"start_listen"}""")
                binding.btnListen.text = getString(R.string.listen_stop)
                binding.tvChildStatus.text = getString(R.string.child_status_streaming)
            } else {
                player?.release()
                player = null
                WebSocketManager.send("""{"type":"stop_listen"}""")
                binding.btnListen.text = getString(R.string.listen_start)
                binding.tvChildStatus.text = getString(R.string.child_status_idle)
                binding.progressVolume.progress = 0
            }
        }
    }

    private fun setupWebSocket() {
        WebSocketManager.onConnected = {
            runOnUiThread {
                binding.tvServerStatus.text = getString(R.string.status_connected)
            }
            WebSocketManager.send("""{"type":"register","role":"parent"}""")
        }
        WebSocketManager.onDisconnected = {
            runOnUiThread { binding.tvServerStatus.text = getString(R.string.status_disconnected) }
        }
        WebSocketManager.onTextMessage = { text ->
            val msg = JSONObject(text)
            if (msg.getString("type") == "status") {
                val count = msg.getInt("listeningCount")
                runOnUiThread {
                    binding.tvOtherParent.visibility =
                        if (count >= 2) View.VISIBLE else View.GONE
                }
            }
        }
        WebSocketManager.onBinaryMessage = { bytes ->
            val pcm = bytes.toByteArray()
            player?.write(pcm)
            val vol = (VolumeAnalyzer.rms(pcm) * 100).toInt().coerceIn(0, 100)
            runOnUiThread { binding.progressVolume.progress = vol }
        }
        val prefs = getSharedPreferences(App.PREF_NAME, Context.MODE_PRIVATE)
        val serverUrl = prefs.getString(App.PREF_SERVER_URL, App.DEFAULT_SERVER_URL) ?: App.DEFAULT_SERVER_URL
        val idToken = prefs.getString("idToken", "") ?: ""
        WebSocketManager.connectWithAuth(serverUrl, idToken) { /* familyId */ }
    }

    override fun onDestroy() {
        player?.release()
        WebSocketManager.disconnect()
        super.onDestroy()
    }
}
