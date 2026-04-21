package com.formykids

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel(NOTIFICATION_CHANNEL_ID, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW)
        createNotificationChannel(DANGER_CHANNEL_ID, "위험 감지 알림", NotificationManager.IMPORTANCE_HIGH)
    }

    private fun createNotificationChannel(id: String, name: String, importance: Int) {
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(id, name, importance))
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "for_my_kids"
        const val DANGER_CHANNEL_ID = "danger_alerts"
        const val PREF_NAME = "for_my_kids_prefs"
        const val PREF_SERVER_URL = "server_url"
        const val DEFAULT_SERVER_URL = "wss://YOUR_CLOUD_RUN_URL"
        const val ROLE_PARENT = "parent"
        const val ROLE_CHILD = "child"
    }
}
