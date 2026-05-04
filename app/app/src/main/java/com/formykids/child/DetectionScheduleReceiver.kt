package com.formykids.child

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.Calendar

class DetectionScheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> startDetectionService(context)
            ACTION_START_DETECTION -> {
                val serviceIntent = Intent(context, DangerDetectionService::class.java)
                    .setAction(DangerDetectionService.ACTION_START_DETECTION)
                context.startForegroundService(serviceIntent)
            }
            ACTION_STOP_DETECTION -> {
                val serviceIntent = Intent(context, DangerDetectionService::class.java)
                    .setAction(DangerDetectionService.ACTION_STOP_DETECTION)
                context.startForegroundService(serviceIntent)
            }
        }
    }

    private fun startDetectionService(context: Context) {
        context.startForegroundService(Intent(context, AudioStreamService::class.java))
        context.startForegroundService(Intent(context, DangerDetectionService::class.java))
    }

    companion object {
        const val ACTION_START_DETECTION = "com.formykids.SCHEDULE_START_DETECTION"
        const val ACTION_STOP_DETECTION = "com.formykids.SCHEDULE_STOP_DETECTION"

        fun rescheduleAlarms(context: Context, startHours: IntArray, endHours: IntArray) {
            val alarmManager = context.getSystemService(AlarmManager::class.java)
            cancelAlarms(context, alarmManager)
            startHours.forEachIndexed { i, hour ->
                scheduleDaily(context, alarmManager, hour, ACTION_START_DETECTION, 100 + i)
            }
            endHours.forEachIndexed { i, hour ->
                scheduleDaily(context, alarmManager, hour, ACTION_STOP_DETECTION, 200 + i)
            }
        }

        private fun scheduleDaily(
            context: Context,
            alarmManager: AlarmManager,
            hour: Int,
            action: String,
            requestCode: Int
        ) {
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
            }
            val pi = PendingIntent.getBroadcast(
                context, requestCode,
                Intent(context, DetectionScheduleReceiver::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP, cal.timeInMillis, AlarmManager.INTERVAL_DAY, pi
            )
        }

        private fun cancelAlarms(context: Context, alarmManager: AlarmManager) {
            for (requestCode in (100..120) + (200..220)) {
                val pi = PendingIntent.getBroadcast(
                    context, requestCode,
                    Intent(context, DetectionScheduleReceiver::class.java),
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                ) ?: continue
                alarmManager.cancel(pi)
            }
        }
    }
}
