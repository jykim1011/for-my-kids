package com.formykids.parent

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.formykids.App
import com.formykids.R
import com.formykids.WebSocketManager
import okio.ByteString

class AudioListenService : Service() {

    private var player: AudioPlayer? = null
    private lateinit var audioManager: AudioManager

    companion object {
        const val ACTION_START = "com.formykids.ACTION_START_LISTEN"
        const val ACTION_STOP = "com.formykids.ACTION_STOP_LISTEN"
        const val ACTION_SPEAKER_ON = "com.formykids.ACTION_SPEAKER_ON"
        const val ACTION_SPEAKER_OFF = "com.formykids.ACTION_SPEAKER_OFF"
        const val EXTRA_GAIN_PROGRESS = "gain_progress"
        private const val NOTIFICATION_ID = 2

        @Volatile var isListening = false
        @Volatile var isSpeakerphone = false
        var onVolumeUpdate: ((Int) -> Unit)? = null
        var onListeningChanged: ((Boolean) -> Unit)? = null
        @Volatile var instance: AudioListenService? = null
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!::audioManager.isInitialized) {
            audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        }
        when (intent?.action) {
            ACTION_START -> startListening(intent.getIntExtra(EXTRA_GAIN_PROGRESS, 0))
            ACTION_STOP -> stopListening()
            ACTION_SPEAKER_ON -> setSpeaker(true)
            ACTION_SPEAKER_OFF -> setSpeaker(false)
        }
        return START_NOT_STICKY
    }

    private fun startListening(gainProgress: Int = 0) {
        if (isListening) return
        isListening = true
        isSpeakerphone = true
        player = AudioPlayer()
        if (gainProgress > 0) {
            player?.gainFactor = (1.0f + (gainProgress / 20f) * 2.0f).coerceIn(1.0f, 3.0f)
        }
        routeSpeaker(true)
        WebSocketManager.send("""{"type":"start_listen"}""")
        WebSocketManager.onBinaryMessage = { bytes: ByteString ->
            val pcm = bytes.toByteArray()
            player?.write(pcm)
            val vol = (VolumeAnalyzer.rms(pcm) * 100).toInt().coerceIn(0, 100)
            onVolumeUpdate?.invoke(vol)
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        onListeningChanged?.invoke(true)
    }

    private fun stopListening() {
        if (!isListening) return
        isListening = false
        isSpeakerphone = false
        onListeningChanged?.invoke(false)
        WebSocketManager.onBinaryMessage = null
        WebSocketManager.send("""{"type":"stop_listen"}""")
        player?.release()
        player = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun setSpeaker(on: Boolean) {
        if (!isListening) return
        routeSpeaker(on)
        isSpeakerphone = on
    }

    private fun routeSpeaker(on: Boolean) {
        val device = if (on) {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        } else null
        player?.setPreferredDevice(device)
    }

    fun setGain(factor: Float) {
        player?.gainFactor = factor.coerceIn(1.0f, 3.0f)
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, AudioListenService::class.java).setAction(ACTION_STOP)
        val stopPi = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)
        val openIntent = Intent(this, ParentActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPi = PendingIntent.getActivity(this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, App.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_listening_title))
            .setContentText(getString(R.string.notification_listening_content))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setContentIntent(openPi)
            .addAction(android.R.drawable.ic_media_pause, getString(R.string.listen_stop), stopPi)
            .build()
    }

    override fun onDestroy() {
        instance = null
        if (isListening) stopListening()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
