package com.formykids.auth

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.formykids.App
import com.formykids.databinding.ActivityRoleSelectBinding

class RoleSelectActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRoleSelectBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRoleSelectBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnParent.setOnClickListener { launch(App.ROLE_PARENT) }
        binding.btnChild.setOnClickListener { launch(App.ROLE_CHILD) }
    }

    private fun launch(role: String) {
        startActivity(Intent(this, PairingActivity::class.java).putExtra("role", role))
        finish()
    }
}
