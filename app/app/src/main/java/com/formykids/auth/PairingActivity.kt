package com.formykids.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.formykids.App
import com.formykids.child.ChildActivity
import com.formykids.databinding.ActivityPairingBinding
import com.formykids.parent.ParentActivity
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

class PairingActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPairingBinding
    private val role by lazy { intent.getStringExtra("role") ?: App.ROLE_CHILD }
    private val db get() = Firebase.firestore
    private val uid get() = Firebase.auth.currentUser!!.uid

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPairingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (role == App.ROLE_PARENT) setupParentFlow() else setupChildFlow()
    }

    private fun setupParentFlow() {
        binding.layoutParent.visibility = View.VISIBLE
        val code = String.format("%06d", Random.nextInt(1000000))
        binding.tvPairingCode.text = code
        lifecycleScope.launch {
            val familyRef = db.collection("families").document()
            familyRef.set(mapOf(
                "parentUids" to listOf(uid),
                "childUid" to null,
                "pairingCode" to code,
                "pairingExpiresAt" to System.currentTimeMillis() + 600_000
            )).await()
            db.collection("users").document(uid).set(mapOf(
                "role" to App.ROLE_PARENT,
                "familyId" to familyRef.id,
                "fcmToken" to "",
                "createdAt" to System.currentTimeMillis()
            )).await()
        }
        binding.btnCodeDone.setOnClickListener {
            startActivity(Intent(this, ParentActivity::class.java))
            finish()
        }
    }

    private fun setupChildFlow() {
        binding.layoutChild.visibility = View.VISIBLE
        binding.btnSubmitCode.setOnClickListener {
            val code = binding.etCode.text.toString().trim()
            lifecycleScope.launch { submitCode(code) }
        }
    }

    private suspend fun submitCode(code: String) {
        val snap = db.collection("families")
            .whereEqualTo("pairingCode", code)
            .whereGreaterThan("pairingExpiresAt", System.currentTimeMillis())
            .get().await()
        if (snap.isEmpty) {
            runOnUiThread { Toast.makeText(this, "코드가 올바르지 않거나 만료되었습니다", Toast.LENGTH_SHORT).show() }
            return
        }
        val familyDoc = snap.documents.first()
        val familyId = familyDoc.id
        familyDoc.reference.update("childUid", uid).await()
        db.collection("users").document(uid).set(mapOf(
            "role" to App.ROLE_CHILD,
            "familyId" to familyId,
            "fcmToken" to "",
            "createdAt" to System.currentTimeMillis()
        )).await()
        startActivity(Intent(this, ChildActivity::class.java))
        finish()
    }
}
