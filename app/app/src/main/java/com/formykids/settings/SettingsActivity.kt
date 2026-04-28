package com.formykids.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.formykids.App
import com.formykids.SplashActivity
import com.formykids.databinding.ActivitySettingsBinding
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = getSharedPreferences(App.PREF_NAME, MODE_PRIVATE)
        binding.etServerUrl.setText(prefs.getString(App.PREF_SERVER_URL, App.DEFAULT_SERVER_URL))

        binding.btnSaveUrl.setOnClickListener {
            prefs.edit().putString(App.PREF_SERVER_URL, binding.etServerUrl.text.toString().trim()).apply()
            finish()
        }

        binding.btnLogout.setOnClickListener {
            Firebase.auth.signOut()
            prefs.edit().clear().apply()
            startActivity(Intent(this, SplashActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        }

        binding.btnManageSubscription.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/account/subscriptions")))
        }

        binding.btnBack.setOnClickListener {
            finish()
        }
    }
}
