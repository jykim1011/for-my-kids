package com.formykids

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.formykids.child.ChildActivity
import com.formykids.parent.ParentActivity
import com.formykids.databinding.ActivityModeSelectBinding

class ModeSelectActivity : AppCompatActivity() {

    private lateinit var binding: ActivityModeSelectBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(App.PREF_NAME, MODE_PRIVATE)
        val role = prefs.getString("role", null)
        if (role != null) {
            launchRoleActivity(role)
            return
        }

        binding = ActivityModeSelectBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnParent.setOnClickListener { saveRoleAndLaunch(App.ROLE_PARENT) }
        binding.btnChild.setOnClickListener { saveRoleAndLaunch(App.ROLE_CHILD) }
    }

    private fun saveRoleAndLaunch(role: String) {
        getSharedPreferences(App.PREF_NAME, MODE_PRIVATE)
            .edit().putString("role", role).apply()
        launchRoleActivity(role)
    }

    private fun launchRoleActivity(role: String) {
        val target = if (role == App.ROLE_PARENT) ParentActivity::class.java
                     else ChildActivity::class.java
        startActivity(Intent(this, target))
        finish()
    }
}
