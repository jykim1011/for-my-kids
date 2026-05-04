package com.formykids.parent

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.formykids.App
import com.formykids.R
import com.formykids.WebSocketManager
import com.formykids.databinding.ActivityParentBinding
import com.formykids.settings.SettingsActivity
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import kotlin.random.Random

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

        binding.btnConnectChild?.setOnClickListener {
            generateAndShowChildPairingCode()
        }

        binding.btnInviteParent?.setOnClickListener {
            generateAndShowInviteCode()
        }

        binding.btnListen.setOnClickListener {
            if (AudioListenService.isListening) {
                startService(Intent(this, AudioListenService::class.java).setAction(AudioListenService.ACTION_STOP))
                binding.tvListenLabel.text = getString(R.string.listen_start)
                binding.tvChildStatus.text = getString(R.string.child_status_idle)
                binding.progressVolume.progress = 0
                binding.layoutSpeaker.visibility = View.GONE
                setVolumeControlStream(AudioManager.USE_DEFAULT_STREAM_TYPE)
            } else {
                startService(Intent(this, AudioListenService::class.java).setAction(AudioListenService.ACTION_START))
                binding.tvListenLabel.text = getString(R.string.listen_stop)
                binding.tvChildStatus.text = getString(R.string.child_status_streaming)
                binding.layoutSpeaker.visibility = View.VISIBLE
                binding.btnSpeaker.setImageResource(R.drawable.ic_volume_off)
                setVolumeControlStream(AudioManager.STREAM_VOICE_CALL)
            }
        }

        binding.btnSpeaker.setOnClickListener {
            if (AudioListenService.isSpeakerphone) {
                startService(Intent(this, AudioListenService::class.java)
                    .setAction(AudioListenService.ACTION_SPEAKER_OFF))
                binding.btnSpeaker.setImageResource(R.drawable.ic_volume_off)
            } else {
                startService(Intent(this, AudioListenService::class.java)
                    .setAction(AudioListenService.ACTION_SPEAKER_ON))
                binding.btnSpeaker.setImageResource(R.drawable.ic_volume_up)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AudioListenService.onVolumeUpdate = { vol -> runOnUiThread { binding.progressVolume.progress = vol } }
        val listening = AudioListenService.isListening
        binding.tvListenLabel.text = if (listening) getString(R.string.listen_stop) else getString(R.string.listen_start)
        binding.tvChildStatus.text = if (listening) getString(R.string.child_status_streaming) else getString(R.string.child_status_idle)
        if (!listening) binding.progressVolume.progress = 0
        if (listening) {
            binding.layoutSpeaker.visibility = View.VISIBLE
            binding.btnSpeaker.setImageResource(
                if (AudioListenService.isSpeakerphone) R.drawable.ic_volume_up
                else R.drawable.ic_volume_off
            )
            setVolumeControlStream(AudioManager.STREAM_VOICE_CALL)
        } else {
            binding.layoutSpeaker.visibility = View.GONE
            setVolumeControlStream(AudioManager.USE_DEFAULT_STREAM_TYPE)
        }
    }

    override fun onPause() {
        super.onPause()
        AudioListenService.onVolumeUpdate = null
        setVolumeControlStream(AudioManager.USE_DEFAULT_STREAM_TYPE)
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

    private fun generateAndShowChildPairingCode() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val uid = Firebase.auth.currentUser?.uid ?: return@launch
                val userDoc = Firebase.firestore.collection("users").document(uid).get().await()
                val familyId = userDoc.getString("familyId") ?: return@launch
                val code = String.format("%06d", Random.nextInt(1000000))
                Firebase.firestore.collection("families").document(familyId)
                    .update(mapOf(
                        "pairingCode" to code,
                        "pairingExpiresAt" to System.currentTimeMillis() + 600_000
                    )).await()
                runOnUiThread {
                    AlertDialog.Builder(this@ParentActivity)
                        .setTitle(getString(R.string.child_pairing_code_title))
                        .setMessage("${getString(R.string.child_pairing_code_message)}\n\n$code")
                        .setPositiveButton(getString(R.string.child_pairing_code_copy)) { _, _ ->
                            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("pairing_code", code))
                            Toast.makeText(this@ParentActivity, getString(R.string.child_pairing_code_copied), Toast.LENGTH_SHORT).show()
                        }
                        .setNegativeButton(getString(R.string.btn_close), null)
                        .show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@ParentActivity, getString(R.string.child_pairing_code_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun generateAndShowInviteCode() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val uid = Firebase.auth.currentUser?.uid ?: return@launch
                val userDoc = Firebase.firestore.collection("users").document(uid).get().await()
                val familyId = userDoc.getString("familyId") ?: return@launch
                val code = String.format("%06d", Random.nextInt(1000000))
                Firebase.firestore.collection("families").document(familyId)
                    .update(mapOf(
                        "inviteCode" to code,
                        "inviteExpiresAt" to System.currentTimeMillis() + 600_000
                    )).await()
                runOnUiThread { showInviteDialog(code) }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@ParentActivity, "초대코드 생성에 실패했습니다", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showInviteDialog(code: String) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.invite_code_title))
            .setMessage("${getString(R.string.invite_code_message)}\n\n$code")
            .setPositiveButton(getString(R.string.invite_code_copy)) { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("invite_code", code))
                Toast.makeText(this, getString(R.string.invite_code_copied), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.btn_close), null)
            .show()
    }

    override fun onDestroy() {
        if (!AudioListenService.isListening) {
            WebSocketManager.disconnect()
        }
        super.onDestroy()
    }
}
