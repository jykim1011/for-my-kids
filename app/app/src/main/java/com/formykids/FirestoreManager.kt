package com.formykids

import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

object FirestoreManager {
    private val db get() = Firebase.firestore
    private val auth get() = Firebase.auth

    suspend fun getCurrentUser(): Map<String, Any?>? {
        val uid = auth.currentUser?.uid ?: return null
        return db.collection("users").document(uid).get().await().data
    }

    suspend fun updateFcmToken(token: String) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).update("fcmToken", token).await()
    }

    suspend fun getSubscription(): Map<String, Any?>? {
        val uid = auth.currentUser?.uid ?: return null
        return db.collection("subscriptions").document(uid).get().await().data
    }

    suspend fun isPremium(): Boolean {
        val sub = getSubscription() ?: return false
        val plan = sub["plan"] as? String ?: return false
        val expiresAt = sub["expiresAt"] as? Long ?: return false
        return plan == "premium" && expiresAt > System.currentTimeMillis()
    }

    suspend fun getAlerts(familyId: String): List<Map<String, Any?>> {
        return db.collection("alerts")
            .whereEqualTo("familyId", familyId)
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(50)
            .get().await()
            .documents.map { it.data ?: emptyMap() }
    }

    suspend fun saveAlert(
        familyId: String,
        type: String,
        confidence: Float,
        clipUrl: String?,
        clipExpiresAt: Long?
    ): String {
        val data = mutableMapOf<String, Any>(
            "familyId" to familyId,
            "timestamp" to System.currentTimeMillis(),
            "type" to type,
            "confidence" to confidence.toDouble()
        )
        clipUrl?.let { data["clipUrl"] = it }
        clipExpiresAt?.let { data["clipExpiresAt"] = it }
        return db.collection("alerts").add(data).await().id
    }

    fun observeDetectionSettings(
        familyId: String,
        onUpdate: (enabled: Boolean, schedule: List<Pair<Int, Int>>) -> Unit
    ): ListenerRegistration {
        return db.collection("families").document(familyId)
            .addSnapshotListener { snapshot, _ ->
                val enabled = snapshot?.getBoolean("detectionEnabled") ?: false
                @Suppress("UNCHECKED_CAST")
                val raw = snapshot?.get("detectionSchedule") as? List<Map<String, Any>> ?: emptyList()
                val schedule = raw.map { m ->
                    val start = (m["startHour"] as? Long)?.toInt() ?: 0
                    val end = (m["endHour"] as? Long)?.toInt() ?: 24
                    start to end
                }
                onUpdate(enabled, schedule)
            }
    }

    suspend fun updateDetectionSettings(
        familyId: String,
        enabled: Boolean,
        schedule: List<Pair<Int, Int>>
    ) {
        val scheduleData = schedule.map { (start, end) ->
            mapOf("startHour" to start, "endHour" to end)
        }
        db.collection("families").document(familyId)
            .update(mapOf("detectionEnabled" to enabled, "detectionSchedule" to scheduleData))
            .await()
    }
}
