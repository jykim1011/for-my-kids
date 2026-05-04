package com.formykids

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.formykids.auth.OnboardingActivity
import com.formykids.auth.WelcomeActivity
import com.formykids.child.ChildActivity
import com.formykids.parent.ParentActivity
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val crashPrefs = getSharedPreferences(App.PREF_CRASH, Context.MODE_PRIVATE)
        val trace = crashPrefs.getString(App.PREF_CRASH_TRACE, null)
        if (trace != null) {
            crashPrefs.edit().remove(App.PREF_CRASH_TRACE).apply()
            val tv = TextView(this).apply {
                text = trace
                textSize = 10f
                setPadding(32, 32, 32, 32)
                setTextIsSelectable(true)
            }
            AlertDialog.Builder(this)
                .setTitle("크래시 로그")
                .setView(ScrollView(this).apply { addView(tv) })
                .setPositiveButton("확인") { _, _ -> recreate() }
                .setCancelable(false)
                .show()
            return
        }

        val prefs = getSharedPreferences(App.PREF_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(App.PREF_WELCOME_SHOWN, false)) {
            startActivity(Intent(this, WelcomeActivity::class.java))
            finish()
            return
        }

        val user = Firebase.auth.currentUser
        if (user == null) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            lifecycleScope.launch {
                try { FirestoreManager.updateFcmToken(token) } catch (_: Exception) {}
            }
        }
        lifecycleScope.launch {
            try {
                val userData = FirestoreManager.getCurrentUser()
                val role = userData?.get("role") as? String
                val familyId = userData?.get("familyId") as? String
                if (role == null || familyId.isNullOrEmpty()) {
                    startActivity(Intent(this@SplashActivity, com.formykids.auth.RoleSelectActivity::class.java))
                    finish()
                    return@launch
                }
                val target = if (role == App.ROLE_PARENT) ParentActivity::class.java else ChildActivity::class.java
                startActivity(Intent(this@SplashActivity, target))
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@SplashActivity, "네트워크 오류. 앱을 다시 시작해주세요.", Toast.LENGTH_LONG).show()
            }
        }
    }
}
