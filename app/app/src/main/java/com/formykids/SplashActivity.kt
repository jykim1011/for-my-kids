package com.formykids

import android.content.Context
import android.content.Intent
import android.os.Bundle
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
            lifecycleScope.launch { FirestoreManager.updateFcmToken(token) }
        }
        lifecycleScope.launch {
            val userData = FirestoreManager.getCurrentUser()
            val role = userData?.get("role") as? String
            val target = if (role == App.ROLE_PARENT) ParentActivity::class.java else ChildActivity::class.java
            startActivity(Intent(this@SplashActivity, target))
            finish()
        }
    }
}
