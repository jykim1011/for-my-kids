package com.formykids

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.formykids.BuildConfig
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Firebase.auth.firebaseAuthSettings.setAppVerificationDisabledForTesting(true)
        }
        setupCrashHandler()
        createNotificationChannel(NOTIFICATION_CHANNEL_ID, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW)
        createNotificationChannel(DANGER_CHANNEL_ID, "위험 감지 알림", NotificationManager.IMPORTANCE_HIGH)
    }

    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            getSharedPreferences(PREF_CRASH, MODE_PRIVATE).edit()
                .putString(PREF_CRASH_TRACE, throwable.stackTraceToString())
                .commit()
            defaultHandler?.uncaughtException(thread, throwable)
        }
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
        const val PREF_WELCOME_SHOWN = "welcome_shown"
        const val DEFAULT_SERVER_URL = "wss://family-monitor-relay-tz6prc3acq-du.a.run.app"
        const val ROLE_PARENT = "parent"
        const val ROLE_CHILD = "child"
        const val PREF_CRASH = "crash_log"
        const val PREF_CRASH_TRACE = "trace"
    }
}
