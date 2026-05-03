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
        // Detection loop added in Task 6
    }

    internal fun stopDetection() {
        detecting = false
        detectionJob?.cancel()
        updateNotification(false)
    }

    private fun updateNotification(active: Boolean) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(active))
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
