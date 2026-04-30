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
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

class ParentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityParentBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityParentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWebSocket()

        binding.btnSettings?.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.btnAlertHistory?.setOnClickListener {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, AlertHistoryFragment())
                .addToBackStack(null)
                .commit()
        }

        binding.btnListen.setOnClickListener {
            if (AudioListenService.isListening) {
                startService(Intent(this, AudioListenService::class.java).setAction(AudioListenService.ACTION_STOP))
                binding.tvListenLabel.text = getString(R.string.listen_start)
                binding.tvChildStatus.text = getString(R.string.child_status_idle)
                binding.progressVolume.progress = 0
            } else {
                startService(Intent(this, AudioListenService::class.java).setAction(AudioListenService.ACTION_START))
                binding.tvListenLabel.text = getString(R.string.listen_stop)
                binding.tvChildStatus.text = getString(R.string.child_status_streaming)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AudioListenService.onVolumeUpdate = { vol -> binding.progressVolume.progress = vol }
        val listening = AudioListenService.isListening
        binding.tvListenLabel.text = if (listening) getString(R.string.listen_stop) else getString(R.string.listen_start)
        binding.tvChildStatus.text = if (listening) getString(R.string.child_status_streaming) else getString(R.string.child_status_idle)
        if (!listening) binding.progressVolume.progress = 0
    }

    override fun onPause() {
        super.onPause()
        AudioListenService.onVolumeUpdate = null
    }

    private fun setupWebSocket() {
        WebSocketManager.onConnected = {
            runOnUiThread {
                binding.tvServerStatus.text = getString(R.string.status_connected)
            }
            if (AudioListenService.isListening) {
                WebSocketManager.send("""{"type":"start_listen"}""")
            }
        }
        WebSocketManager.onDisconnected = {
            runOnUiThread { binding.tvServerStatus.text = getString(R.string.status_disconnected) }
        }
        WebSocketManager.onTextMessage = { text ->
            val msg = JSONObject(text)
            if (msg.optString("type") == "status") {
                val count = msg.getInt("listeningCount")
                runOnUiThread {
                    binding.tvOtherParent.visibility =
                        if (count >= 2) View.VISIBLE else View.GONE
                }
            }
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val idToken = Firebase.auth.currentUser
                ?.getIdToken(false)?.await()?.token ?: return@launch
            val prefs = getSharedPreferences(App.PREF_NAME, Context.MODE_PRIVATE)
            val serverUrl = prefs.getString(App.PREF_SERVER_URL, App.DEFAULT_SERVER_URL) ?: App.DEFAULT_SERVER_URL
            WebSocketManager.connectWithAuth(
                serverUrl,
                idToken,
                tokenRefresher = { Firebase.auth.currentUser?.getIdToken(true)?.await()?.token }
            ) { }
        }
    }

    override fun onDestroy() {
        if (!AudioListenService.isListening) {
            WebSocketManager.disconnect()
        }
        super.onDestroy()
    }
}
