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
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.UUID

class DangerDetectionService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    var detecting = false
        private set
    private var detectionJob: Job? = null
    var familyId: String? = null
        private set
    private var settingsListener: ListenerRegistration? = null

    private val ringBuffer = RingBuffer(RING_BUFFER_BYTES)
    @Volatile private var dangerDetector: com.formykids.DangerDetector? = null
    @Volatile private var postDetectionChunksLeft = 0
    @Volatile private var pendingDetection: com.formykids.DangerDetector.Detection? = null

    var inferenceIntervalMs = 2000L
        private set

    private var batteryReceiverRegistered = false

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
        // Ring buffer holds 15s. After detection, we record 5s more (POST_DETECTION_CHUNKS).
        // read(CLIP_BYTES) then extracts the last 10s = 5s pre-detection + 5s post-detection.
        const val RING_BUFFER_BYTES = 480_000   // 15s × 16kHz × 2 bytes
        const val CLIP_BYTES = 320_000          // 10s × 16kHz × 2 bytes
        const val POST_DETECTION_CHUNKS = 50    // 5s post-detection at 100ms chunks
        const val VAD_THRESHOLD = 0.01f
        val httpClient = OkHttpClient()
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification(false))
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        batteryReceiverRegistered = true
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
        val text = if (active) getString(R.string.detection_active) else getString(R.string.detection_standby)
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
        val job = detectionJob
        detectionJob = null
        scope.launch {
            job?.cancelAndJoin()
            dangerDetector?.close()
            dangerDetector = null
            postDetectionChunksLeft = 0
            pendingDetection = null
            updateNotification(false)
        }
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
        if (recorder.state != android.media.AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return
        }
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
            if (recorder.recordingState == android.media.AudioRecord.RECORDSTATE_RECORDING) {
                recorder.stop()
            }
            recorder.release()
        }
    }

    private suspend fun uploadClipAndAlert(
        detection: com.formykids.DangerDetector.Detection,
        pcm: ByteArray
    ) {
        val fid = familyId ?: return
        val alertId = UUID.randomUUID().toString()
        val clipFile = File(cacheDir, "clips/$alertId.m4a").also { it.parentFile?.mkdirs() }

        try {
            withContext(Dispatchers.Default) {
                ClipEncoder.encodeToAac(pcm, clipFile)
            }

            val storageRef = Firebase.storage.reference.child("clips/$fid/$alertId.m4a")
            storageRef.putFile(android.net.Uri.fromFile(clipFile)).await()
            val clipUrl = storageRef.downloadUrl.await().toString()
            // clipExpiresAt is stored for client-side display. Actual Storage deletion requires
            // a Firebase Storage lifecycle rule or Cloud Function; configure in Firebase console.
            val clipExpiresAt = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000

            FirestoreManager.saveAlert(fid, detection.type, detection.confidence, clipUrl, clipExpiresAt)

            val idToken = Firebase.auth.currentUser?.getIdToken(false)?.await()?.token ?: return
            triggerFcm(idToken, fid, detection.type, detection.confidence)
        } catch (e: Exception) {
            android.util.Log.e("DangerDetection", "Clip pipeline failed: ${e.message}")
        } finally {
            clipFile.delete()
        }
    }

    private suspend fun triggerFcm(idToken: String, familyId: String, type: String, confidence: Float) {
        val prefs = getSharedPreferences(App.PREF_NAME, MODE_PRIVATE)
        val wsUrl = prefs.getString(App.PREF_SERVER_URL, App.DEFAULT_SERVER_URL)!!
        val httpUrl = wsUrl.replaceFirst("wss://", "https://").replaceFirst("ws://", "http://") + "/alert"

        val body = JSONObject().apply {
            put("idToken", idToken)
            put("familyId", familyId)
            put("type", type)
            put("confidence", confidence)
        }.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder().url(httpUrl).post(body).build()
        withContext(Dispatchers.IO) {
            httpClient.newCall(request).execute().close()
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val restart = PendingIntent.getService(
            this, 0,
            Intent(applicationContext, DangerDetectionService::class.java),
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        (getSystemService(ALARM_SERVICE) as AlarmManager)
            .set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 1000L, restart)
    }

    override fun onDestroy() {
        stopDetection()
        settingsListener?.remove()
        if (batteryReceiverRegistered) unregisterReceiver(batteryReceiver)
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
