package com.formykids.child

import android.app.*
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.formykids.App
import com.formykids.DangerDetector
import com.formykids.FirestoreManager
import com.formykids.R
import com.formykids.WebSocketManager
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

class AudioStreamService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var streamingJob: Job? = null
    private var streaming = false
    private var dangerDetector: DangerDetector? = null

    companion object {
        const val NOTIFICATION_ID = 1
        var statusCallback: ((Boolean) -> Unit)? = null
        var connectionCallback: ((Boolean) -> Unit)? = null
        var limitCallback: (() -> Unit)? = null
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        setupWebSocket()
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, App.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()

    private fun setupWebSocket() {
        scope.launch {
            val idToken = Firebase.auth.currentUser
                ?.getIdToken(false)?.await()?.token ?: return@launch
            val prefs = getSharedPreferences(App.PREF_NAME, MODE_PRIVATE)
            val serverUrl = prefs.getString(App.PREF_SERVER_URL, App.DEFAULT_SERVER_URL)!!
            WebSocketManager.connectWithAuth(serverUrl, idToken) { _ ->
                connectionCallback?.invoke(true)
            }
            WebSocketManager.onDisconnected = { connectionCallback?.invoke(false) }
            WebSocketManager.onTextMessage = { text ->
                val msg = JSONObject(text)
                when (msg.optString("type")) {
                    "start_stream" -> startStreaming()
                    "stop_stream" -> stopStreaming()
                    "stream_limit_reached" -> {
                        stopStreaming()
                        statusCallback?.invoke(false)
                        limitCallback?.invoke()
                    }
                }
            }
        }
    }

    private fun startStreaming() {
        if (streaming) return
        streaming = true
        statusCallback?.invoke(true)
        streamingJob = scope.launch { captureLoop() }
    }

    private fun stopStreaming() {
        streaming = false
        streamingJob?.cancel()
        statusCallback?.invoke(false)
    }

    private suspend fun captureLoop() {
        // Await premium check before starting — prevents race condition where
        // isPremium is false for first few seconds even for paying users.
        val isPremium = FirestoreManager.isPremium()

        if (isPremium && dangerDetector == null) {
            dangerDetector = DangerDetector(this@AudioStreamService)
        }

        val sampleRate = 16000
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val bufSize = maxOf(minBuf, 6400)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC, sampleRate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize
        )
        recorder.startRecording()
        val chunk = ByteArray(3200) // 100ms at 16kHz
        val analysisBuffer = ByteArray(31200) // ~1s at 16kHz for YAMNet
        var analysisPos = 0

        try {
            while (streaming && currentCoroutineContext().isActive) {
                val read = recorder.read(chunk, 0, chunk.size)
                if (read > 0) {
                    val data = chunk.copyOf(read)
                    WebSocketManager.send(data)
                    if (isPremium) {
                        val toCopy = minOf(read, analysisBuffer.size - analysisPos)
                        System.arraycopy(data, 0, analysisBuffer, analysisPos, toCopy)
                        analysisPos += toCopy
                        if (analysisPos >= analysisBuffer.size) {
                            analysisPos = 0
                            val detection = dangerDetector?.analyze(analysisBuffer)
                            if (detection != null) {
                                WebSocketManager.send(
                                    """{"type":"danger_alert","class":"${detection.type}","confidence":${detection.confidence}}"""
                                )
                            }
                        }
                    }
                }
            }
        } finally {
            recorder.stop()
            recorder.release()
        }
    }

    override fun onDestroy() {
        stopStreaming()
        dangerDetector?.close()
        dangerDetector = null
        WebSocketManager.disconnect()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
