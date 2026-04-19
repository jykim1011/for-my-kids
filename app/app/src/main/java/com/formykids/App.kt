package com.formykids

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel),
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "for_my_kids"
        const val PREF_NAME = "for_my_kids_prefs"
        const val PREF_ROLE = "role"
        const val ROLE_PARENT = "parent"
        const val ROLE_CHILD = "child"
        const val SERVER_URL = "ws://jssun.myds.me:612"
    }
}
