package com.formykids.child

import android.app.*
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.formykids.App
import com.formykids.R
import com.formykids.WebSocketManager
import kotlinx.coroutines.*
import org.json.JSONObject

class AudioStreamService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var streamingJob: Job? = null
    private var streaming = false

    companion object {
        const val NOTIFICATION_ID = 1
        var statusCallback: ((Boolean) -> Unit)? = null
        var connectionCallback: ((Boolean) -> Unit)? = null
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
        WebSocketManager.onConnected = {
            WebSocketManager.send("""{"type":"register","role":"child"}""")
            connectionCallback?.invoke(true)
        }
        WebSocketManager.onDisconnected = {
            connectionCallback?.invoke(false)
        }
        WebSocketManager.onTextMessage = { text ->
            val msg = JSONObject(text)
            when (msg.getString("type")) {
                "start_stream" -> startStreaming()
                "stop_stream" -> stopStreaming()
            }
        }
        WebSocketManager.connect()
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

    private fun captureLoop() {
        val sampleRate = 8000
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val bufSize = maxOf(minBuf, 3200)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC, sampleRate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize
        )
        recorder.startRecording()
        val chunk = ByteArray(1600) // 100ms at 8kHz 16-bit mono
        try {
            while (streaming && isActive) {
                val read = recorder.read(chunk, 0, chunk.size)
                if (read > 0) WebSocketManager.send(chunk.copyOf(read))
            }
        } finally {
            recorder.stop()
            recorder.release()
        }
    }

    override fun onDestroy() {
        stopStreaming()
        WebSocketManager.disconnect()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
