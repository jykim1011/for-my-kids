package com.formykids.auth

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.formykids.App
import com.formykids.databinding.ActivityWelcomeBinding

class WelcomeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityWelcomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnStart.setOnClickListener {
            getSharedPreferences(App.PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(App.PREF_WELCOME_SHOWN, true)
                .apply()
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
        }
    }
}
