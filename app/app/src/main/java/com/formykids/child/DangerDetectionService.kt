package com.formykids.child

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.formykids.App
import com.formykids.FirestoreManager
import com.formykids.R
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.*

class DangerDetectionService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    var detecting = false
        private set
    private var detectionJob: Job? = null
    var familyId: String? = null
        private set
    private var settingsListener: ListenerRegistration? = null

    private val ringBuffer = RingBuffer(RING_BUFFER_BYTES)
    private var dangerDetector: com.formykids.DangerDetector? = null
    private var postDetectionChunksLeft = 0
    private var pendingDetection: com.formykids.DangerDetector.Detection? = null

    var inferenceIntervalMs = 2000L
        private set

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 100)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            inferenceIntervalMs = if (level * 100 / scale <= 20) 5000L else 2000L
        }
    }

    companion object {
        const val NOTIFICATION_ID = 2
        const val ACTION_START_DETECTION = "com.formykids.ACTION_START_DETECTION"
        const val ACTION_STOP_DETECTION = "com.formykids.ACTION_STOP_DETECTION"
        const val RING_BUFFER_BYTES = 480_000   // 15s × 16kHz × 2 bytes
        const val CLIP_BYTES = 320_000          // 10s × 16kHz × 2 bytes
        const val POST_DETECTION_CHUNKS = 50    // 5s at 100ms chunks
        const val VAD_THRESHOLD = 0.01f
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification(false))
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        scope.launch { loadFamilyIdAndStartListener() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_DETECTION -> if (!detecting) scope.launch { startDetection() }
            ACTION_STOP_DETECTION -> stopDetection()
        }
        return START_STICKY
    }

    private suspend fun loadFamilyIdAndStartListener() {
        val user = FirestoreManager.getCurrentUser() ?: return
        val fid = user["familyId"] as? String ?: return
        familyId = fid
        settingsListener = FirestoreManager.observeDetectionSettings(fid) { enabled, schedule ->
            val withinSchedule = schedule.isEmpty() || isWithinSchedule(schedule)
            if (enabled && withinSchedule) {
                if (!detecting) scope.launch { startDetection() }
            } else {
                stopDetection()
            }
        }
    }

    private fun isWithinSchedule(schedule: List<Pair<Int, Int>>): Boolean {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return schedule.any { (start, end) ->
            if (start <= end) hour in start until end
            else hour >= start || hour < end
        }
    }

    private fun buildNotification(active: Boolean): Notification {
        val text = if (active) "위기 감지 중" else "감지 대기 중"
        return NotificationCompat.Builder(this, App.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }

    internal suspend fun startDetection() {
        detecting = true
        updateNotification(true)
        if (dangerDetector == null) {
            dangerDetector = com.formykids.DangerDetector(this@DangerDetectionService)
        }
        detectionJob = scope.launch { captureLoop() }
    }

    internal fun stopDetection() {
        detecting = false
        detectionJob?.cancel()
        dangerDetector?.close()
        dangerDetector = null
        postDetectionChunksLeft = 0
        pendingDetection = null
        updateNotification(false)
    }

    private fun updateNotification(active: Boolean) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(active))
    }

    private suspend fun captureLoop() {
        val sampleRate = 16000
        val minBuf = android.media.AudioRecord.getMinBufferSize(
            sampleRate,
            android.media.AudioFormat.CHANNEL_IN_MONO,
            android.media.AudioFormat.ENCODING_PCM_16BIT
        )
        val recorder = android.media.AudioRecord(
            android.media.MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            sampleRate,
            android.media.AudioFormat.CHANNEL_IN_MONO,
            android.media.AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuf, 6400)
        )
        recorder.startRecording()

        val chunk = ByteArray(3200)  // 100ms at 16kHz
        var lastInferenceTime = 0L

        try {
            while (detecting && currentCoroutineContext().isActive) {
                val read = recorder.read(chunk, 0, chunk.size)
                if (read <= 0) continue

                val data = chunk.copyOf(read)
                ringBuffer.write(data)

                // Post-detection countdown: record 5s after detection before extracting clip
                if (postDetectionChunksLeft > 0) {
                    postDetectionChunksLeft--
                    if (postDetectionChunksLeft == 0) {
                        val det = pendingDetection
                        pendingDetection = null
                        if (det != null) {
                            val pcm = ringBuffer.read(CLIP_BYTES)
                            scope.launch { uploadClipAndAlert(det, pcm) }
                        }
                    }
                }

                // VAD + inference at configured interval
                val now = System.currentTimeMillis()
                if (now - lastInferenceTime >= inferenceIntervalMs && postDetectionChunksLeft == 0) {
                    lastInferenceTime = now
                    val sample = ringBuffer.read(com.formykids.DangerDetector.SAMPLE_SIZE * 2)
                    if (ClipEncoder.calculateRms(sample) > VAD_THRESHOLD) {
                        val detection = dangerDetector?.analyze(sample)
                        if (detection != null) {
                            pendingDetection = detection
                            postDetectionChunksLeft = POST_DETECTION_CHUNKS
                        }
                    }
                }
            }
        } finally {
            recorder.stop()
            recorder.release()
        }
    }

    // Clip encode/upload pipeline — implemented in Task 7
    private suspend fun uploadClipAndAlert(
        detection: com.formykids.DangerDetector.Detection,
        pcm: ByteArray
    ) {
        // placeholder
    }

    override fun onDestroy() {
        stopDetection()
        settingsListener?.remove()
        unregisterReceiver(batteryReceiver)
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
