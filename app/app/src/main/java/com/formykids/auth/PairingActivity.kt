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
import com.google.firebase.firestore.FieldValue
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
        binding.layoutParentChoice.visibility = View.VISIBLE

        binding.btnCreateFamily.setOnClickListener {
            binding.layoutParentChoice.visibility = View.GONE
            binding.layoutParent.visibility = View.VISIBLE
            createNewFamily()
        }

        binding.btnJoinFamily.setOnClickListener {
            binding.layoutParentChoice.visibility = View.GONE
            binding.layoutParentJoin.visibility = View.VISIBLE
        }

        binding.btnSubmitInviteCode.setOnClickListener {
            val code = binding.etInviteCode.text.toString().trim()
            lifecycleScope.launch {
                try { joinAsParent(code) } catch (e: Exception) {
                    runOnUiThread { Toast.makeText(this@PairingActivity, "네트워크 오류: ${e.message}", Toast.LENGTH_SHORT).show() }
                }
            }
        }
    }

    private fun createNewFamily() {
        val code = String.format("%06d", Random.nextInt(1000000))
        binding.tvPairingCode.text = code
        lifecycleScope.launch {
            try {
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
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this@PairingActivity, "네트워크 오류: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
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
            lifecycleScope.launch {
                try { submitCode(code) } catch (e: Exception) {
                    runOnUiThread { Toast.makeText(this@PairingActivity, "네트워크 오류: ${e.message}", Toast.LENGTH_SHORT).show() }
                }
            }
        }
    }

    private suspend fun submitCode(code: String) {
        val snap = db.collection("families")
            .whereEqualTo("pairingCode", code)
            .get().await()
        val familyDocs = snap.documents.filter {
            (it.getLong("pairingExpiresAt") ?: 0L) > System.currentTimeMillis()
        }
        if (familyDocs.isEmpty()) {
            runOnUiThread { Toast.makeText(this, "코드가 올바르지 않거나 만료되었습니다", Toast.LENGTH_SHORT).show() }
            return
        }
        val familyDoc = familyDocs.first()
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

    private suspend fun joinAsParent(code: String) {
        val snap = db.collection("families")
            .whereEqualTo("inviteCode", code)
            .get().await()
        val familyDocs = snap.documents.filter {
            (it.getLong("inviteExpiresAt") ?: 0L) > System.currentTimeMillis()
        }
        if (familyDocs.isEmpty()) {
            runOnUiThread { Toast.makeText(this, "코드가 올바르지 않거나 만료되었습니다", Toast.LENGTH_SHORT).show() }
            return
        }
        val familyDoc = familyDocs.first()
        val familyId = familyDoc.id
        familyDoc.reference.update("parentUids", FieldValue.arrayUnion(uid)).await()
        db.collection("users").document(uid).set(mapOf(
            "role" to App.ROLE_PARENT,
            "familyId" to familyId,
            "fcmToken" to "",
            "createdAt" to System.currentTimeMillis()
        )).await()
        runOnUiThread {
            startActivity(Intent(this, ParentActivity::class.java))
            finish()
        }
    }
}
