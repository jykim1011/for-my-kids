# Always-On Danger Detection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Decouple danger detection from the parent's live-listening session so the child's phone detects crises independently, saves 10-second AAC clips to Firebase Storage, and sends FCM alerts to parents via the relay server.

**Architecture:** A new `DangerDetectionService` runs as a foreground service alongside the existing `AudioStreamService`. It uses VAD pre-filtering and runs YAMNet every 2 seconds, maintains a 15-second PCM ring buffer, and on detection records 5 more seconds then encodes a 10-second clip to AAC, uploads it to Firebase Storage, writes the alert to Firestore, and triggers FCM via an HTTP POST to the relay server. Schedule control (on/off + time windows) is stored in Firestore `families/{familyId}` and observed via a snapshot listener inside the service.

**Tech Stack:** Android (Kotlin), TensorFlow Lite (YAMNet already in project), Firebase Firestore + Storage, MediaCodec (AAC encoding), AlarmManager, OkHttp (already in build.gradle), Express (server already uses it)

---

## File Map

| Action | Path | Responsibility |
|---|---|---|
| Create | `child/DangerDetectionService.kt` | Always-on foreground service: audio loop, VAD, YAMNet, clip pipeline, schedule listener |
| Create | `child/RingBuffer.kt` | Thread-safe circular PCM byte buffer |
| Create | `child/ClipEncoder.kt` | RMS calculation (VAD) + PCM→AAC encoding via MediaCodec |
| Create | `child/DetectionScheduleReceiver.kt` | BroadcastReceiver: BOOT_COMPLETED + AlarmManager start/stop |
| Modify | `FirestoreManager.kt` | Add `saveAlert()`, `observeDetectionSettings()`, `updateDetectionSettings()` |
| Modify | `child/ChildActivity.kt` | Start DangerDetectionService alongside AudioStreamService |
| Modify | `AndroidManifest.xml` | Register service, receiver, new permissions |
| Modify | `app/app/build.gradle.kts` | Add firebase-storage-ktx dependency |
| Modify | `settings/SettingsActivity.kt` | Add detection toggle + schedule UI for parent role |
| Modify | `res/layout/activity_settings.xml` | Detection settings section |
| Modify | `parent/AlertAdapter.kt` | Play + download buttons when `clipUrl` present |
| Modify | `res/layout/item_alert.xml` | Add btnPlayClip, btnDownloadClip views |
| Modify | `res/values/strings.xml` | New strings |
| Modify | `server/server.js` | Add `POST /alert` endpoint for FCM trigger |
| Modify | `server/fcm.js` | Export `TYPE_LABELS` + add `sendFcmOnly()` |

---

## Task 1: Add Firebase Storage Dependency

**Files:**
- Modify: `app/app/build.gradle.kts`

- [ ] **Step 1: Add Firebase Storage to build.gradle.kts**

In the `dependencies` block, after the `firebase-messaging-ktx` line add:

```kotlin
implementation("com.google.firebase:firebase-storage-ktx")
```

- [ ] **Step 2: Sync and verify build**

Run: `./gradlew :app:dependencies | grep firebase-storage` (or sync in Android Studio)
Expected: `firebase-storage-ktx` appears in dependency tree

- [ ] **Step 3: Commit**

```
git add app/app/build.gradle.kts
git commit -m "build: add firebase-storage-ktx dependency"
```

---

## Task 2: Extend FirestoreManager

**Files:**
- Modify: `app/app/src/main/java/com/formykids/FirestoreManager.kt`

- [ ] **Step 1: Add imports**

At the top of `FirestoreManager.kt`, add after existing imports:

```kotlin
import com.google.firebase.firestore.ListenerRegistration
```

- [ ] **Step 2: Add saveAlert()**

After `getAlerts()`, add:

```kotlin
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
```

- [ ] **Step 3: Add observeDetectionSettings()**

After `saveAlert()`, add:

```kotlin
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
```

- [ ] **Step 4: Add updateDetectionSettings()**

After `observeDetectionSettings()`, add:

```kotlin
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
```

- [ ] **Step 5: Commit**

```
git add app/app/src/main/java/com/formykids/FirestoreManager.kt
git commit -m "feat: extend FirestoreManager with saveAlert and detection settings methods"
```

---

## Task 3: RingBuffer

**Files:**
- Create: `app/app/src/main/java/com/formykids/child/RingBuffer.kt`
- Create: `app/src/test/java/com/formykids/child/RingBufferTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `app/app/src/test/java/com/formykids/child/RingBufferTest.kt`:

```kotlin
package com.formykids.child

import org.junit.Assert.*
import org.junit.Test

class RingBufferTest {

    @Test
    fun `read from empty buffer returns empty array`() {
        val buf = RingBuffer(100)
        assertTrue(buf.read(10).isEmpty())
    }

    @Test
    fun `read returns last N bytes when buffer not full`() {
        val buf = RingBuffer(1000)
        buf.write(ByteArray(50) { it.toByte() })
        val result = buf.read(10)
        assertEquals(10, result.size)
        assertEquals(40.toByte(), result[0])
        assertEquals(49.toByte(), result[9])
    }

    @Test
    fun `read returns last N bytes after wraparound`() {
        val buf = RingBuffer(100)
        buf.write(ByteArray(60) { it.toByte() })       // bytes 0..59
        buf.write(ByteArray(60) { (it + 60).toByte() }) // bytes 60..119; first 20 overwrite head
        val result = buf.read(60)
        assertEquals(60, result.size)
        assertEquals(60.toByte(), result[0])
        assertEquals(119.toByte(), result[59])
    }

    @Test
    fun `read capped at actual stored size`() {
        val buf = RingBuffer(100)
        buf.write(ByteArray(5) { it.toByte() })
        val result = buf.read(50)
        assertEquals(5, result.size)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:test --tests "com.formykids.child.RingBufferTest"`
Expected: compilation failure (class not found)

- [ ] **Step 3: Implement RingBuffer**

Create `app/app/src/main/java/com/formykids/child/RingBuffer.kt`:

```kotlin
package com.formykids.child

class RingBuffer(private val capacity: Int) {
    private val buffer = ByteArray(capacity)
    private var head = 0
    private var size = 0

    @Synchronized
    fun write(data: ByteArray) {
        var remaining = data.size
        var srcOffset = 0
        while (remaining > 0) {
            val tail = (head + size) % capacity
            val toWrite = minOf(remaining, capacity - tail)
            System.arraycopy(data, srcOffset, buffer, tail, toWrite)
            srcOffset += toWrite
            remaining -= toWrite
            if (size + toWrite <= capacity) {
                size += toWrite
            } else {
                val overflow = (size + toWrite) - capacity
                head = (head + overflow) % capacity
                size = capacity
            }
        }
    }

    @Synchronized
    fun read(length: Int): ByteArray {
        val actual = minOf(length, size)
        if (actual == 0) return ByteArray(0)
        val result = ByteArray(actual)
        val startIdx = (head + size - actual + capacity) % capacity
        val firstChunk = minOf(actual, capacity - startIdx)
        System.arraycopy(buffer, startIdx, result, 0, firstChunk)
        if (firstChunk < actual) {
            System.arraycopy(buffer, 0, result, firstChunk, actual - firstChunk)
        }
        return result
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:test --tests "com.formykids.child.RingBufferTest"`
Expected: All 4 tests PASS

- [ ] **Step 5: Commit**

```
git add app/app/src/main/java/com/formykids/child/RingBuffer.kt \
        app/app/src/test/java/com/formykids/child/RingBufferTest.kt
git commit -m "feat: add RingBuffer with unit tests"
```

---

## Task 4: ClipEncoder

**Files:**
- Create: `app/app/src/main/java/com/formykids/child/ClipEncoder.kt`
- Create: `app/app/src/test/java/com/formykids/child/ClipEncoderTest.kt`

- [ ] **Step 1: Write failing tests for calculateRms**

Create `app/app/src/test/java/com/formykids/child/ClipEncoderTest.kt`:

```kotlin
package com.formykids.child

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ClipEncoderTest {

    @Test
    fun `silent audio returns zero RMS`() {
        val silent = ByteArray(3200)
        assertEquals(0f, ClipEncoder.calculateRms(silent), 0.001f)
    }

    @Test
    fun `max amplitude audio returns near-one RMS`() {
        val buf = ByteBuffer.allocate(3200).order(ByteOrder.LITTLE_ENDIAN)
        repeat(1600) { buf.putShort(Short.MAX_VALUE) }
        val rms = ClipEncoder.calculateRms(buf.array())
        assertTrue("Expected RMS > 0.99, got $rms", rms > 0.99f)
    }

    @Test
    fun `empty array returns zero`() {
        assertEquals(0f, ClipEncoder.calculateRms(ByteArray(0)), 0f)
    }

    @Test
    fun `single byte array returns zero`() {
        assertEquals(0f, ClipEncoder.calculateRms(ByteArray(1)), 0f)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:test --tests "com.formykids.child.ClipEncoderTest"`
Expected: compilation failure

- [ ] **Step 3: Implement ClipEncoder**

Create `app/app/src/main/java/com/formykids/child/ClipEncoder.kt`:

```kotlin
package com.formykids.child

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

object ClipEncoder {

    private const val SAMPLE_RATE = 16000
    private const val BIT_RATE = 32_000
    private const val TIMEOUT_US = 10_000L

    fun calculateRms(pcmBytes: ByteArray): Float {
        if (pcmBytes.size < 2) return 0f
        val buf = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
        var sumSq = 0.0
        val count = pcmBytes.size / 2
        repeat(count) {
            val s = buf.short.toDouble()
            sumSq += s * s
        }
        return (sqrt(sumSq / count) / Short.MAX_VALUE).toFloat()
    }

    fun encodeToAac(pcmBytes: ByteArray, outputFile: File) {
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, 1).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 8192)
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        }
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val info = MediaCodec.BufferInfo()
        var trackIndex = -1
        var muxerStarted = false
        var inputOffset = 0
        var inputDone = false

        try {
            while (true) {
                if (!inputDone) {
                    val idx = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (idx >= 0) {
                        val inputBuf = codec.getInputBuffer(idx)!!
                        val remaining = pcmBytes.size - inputOffset
                        if (remaining > 0) {
                            val n = minOf(remaining, inputBuf.capacity())
                            inputBuf.put(pcmBytes, inputOffset, n)
                            val pts = (inputOffset / 2).toLong() * 1_000_000L / SAMPLE_RATE
                            codec.queueInputBuffer(idx, 0, n, pts, 0)
                            inputOffset += n
                        } else {
                            codec.queueInputBuffer(idx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        }
                    }
                }

                when (val outputIdx = codec.dequeueOutputBuffer(info, TIMEOUT_US)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        trackIndex = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> { /* retry */ }
                    else -> if (outputIdx >= 0) {
                        val outBuf = codec.getOutputBuffer(outputIdx)!!
                        val isConfig = (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                        if (muxerStarted && info.size > 0 && !isConfig) {
                            muxer.writeSampleData(trackIndex, outBuf, info)
                        }
                        codec.releaseOutputBuffer(outputIdx, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                    }
                }
            }
        } finally {
            codec.stop()
            codec.release()
            if (muxerStarted) muxer.stop()
            muxer.release()
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:test --tests "com.formykids.child.ClipEncoderTest"`
Expected: All 4 tests PASS

- [ ] **Step 5: Commit**

```
git add app/app/src/main/java/com/formykids/child/ClipEncoder.kt \
        app/app/src/test/java/com/formykids/child/ClipEncoderTest.kt
git commit -m "feat: add ClipEncoder with VAD RMS calculation and AAC encoding"
```

---

## Task 5: DangerDetectionService — Service Skeleton

**Files:**
- Create: `app/app/src/main/java/com/formykids/child/DangerDetectionService.kt`

- [ ] **Step 1: Create the service file with lifecycle and notification**

Create `app/app/src/main/java/com/formykids/child/DangerDetectionService.kt`:

```kotlin
package com.formykids.child

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.formykids.App
import com.formykids.FirestoreManager
import com.formykids.R
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.*

class DangerDetectionService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Detection state
    private var detecting = false
    private var detectionJob: Job? = null
    private var familyId: String? = null
    private var settingsListener: ListenerRegistration? = null

    // Battery throttle: 2s normally, 5s when ≤ 20%
    var inferenceIntervalMs = 2000L
        private set

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 100)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            inferenceIntervalMs = if (level * 100 / scale <= 20) 5000L else 2000L
        }
    }

    companion object {
        const val NOTIFICATION_ID = 2
        const val ACTION_START_DETECTION = "com.formykids.ACTION_START_DETECTION"
        const val ACTION_STOP_DETECTION = "com.formykids.ACTION_STOP_DETECTION"

        // Ring buffer: 15s × 16000 Hz × 2 bytes/sample
        const val RING_BUFFER_BYTES = 480_000
        // Clip: 10s × 16000 Hz × 2 bytes/sample
        const val CLIP_BYTES = 320_000
        // Post-detection recording: 5s at 100ms chunks
        const val POST_DETECTION_CHUNKS = 50
        // VAD silence threshold (normalized 0..1)
        const val VAD_THRESHOLD = 0.01f
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification(false))
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        scope.launch { loadFamilyIdAndStartListener() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_DETECTION -> if (!detecting) scope.launch { startDetection() }
            ACTION_STOP_DETECTION -> stopDetection()
        }
        return START_STICKY
    }

    private suspend fun loadFamilyIdAndStartListener() {
        val user = FirestoreManager.getCurrentUser() ?: return
        val fid = user["familyId"] as? String ?: return
        familyId = fid
        settingsListener = FirestoreManager.observeDetectionSettings(fid) { enabled, schedule ->
            val withinSchedule = schedule.isEmpty() || isWithinSchedule(schedule)
            if (enabled && withinSchedule) {
                if (!detecting) scope.launch { startDetection() }
            } else {
                stopDetection()
            }
        }
    }

    private fun isWithinSchedule(schedule: List<Pair<Int, Int>>): Boolean {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return schedule.any { (start, end) ->
            if (start <= end) hour in start until end
            else hour >= start || hour < end  // overnight schedule (e.g. 22..6)
        }
    }

    private fun buildNotification(active: Boolean): Notification {
        val text = if (active) getString(R.string.detection_active) else getString(R.string.detection_standby)
        return NotificationCompat.Builder(this, App.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }

    // startDetection() and stopDetection() are added in Task 6
    internal fun startDetection() { detecting = true; updateNotification(true) }
    internal fun stopDetection() { detecting = false; detectionJob?.cancel(); updateNotification(false) }

    private fun updateNotification(active: Boolean) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(active))
    }

    override fun onDestroy() {
        stopDetection()
        settingsListener?.remove()
        unregisterReceiver(batteryReceiver)
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
```

- [ ] **Step 2: Commit skeleton**

```
git add app/app/src/main/java/com/formykids/child/DangerDetectionService.kt
git commit -m "feat: add DangerDetectionService skeleton with battery throttle and schedule listener"
```

---

## Task 6: DangerDetectionService — Detection Loop

**Files:**
- Modify: `app/app/src/main/java/com/formykids/child/DangerDetectionService.kt`

- [ ] **Step 1: Replace stub startDetection/stopDetection with real audio loop**

Replace the two stub lines at the end of `DangerDetectionService` (just before `updateNotification`):

```kotlin
    // Replace these two stubs:
    // internal fun startDetection() { detecting = true; updateNotification(true) }
    // internal fun stopDetection() { detecting = false; detectionJob?.cancel(); updateNotification(false) }
```

With:

```kotlin
    private val ringBuffer = RingBuffer(RING_BUFFER_BYTES)
    private var dangerDetector: DangerDetector? = null

    private var postDetectionChunksLeft = 0
    private var pendingDetection: com.formykids.DangerDetector.Detection? = null

    internal suspend fun startDetection() {
        detecting = true
        updateNotification(true)

        if (dangerDetector == null) {
            dangerDetector = com.formykids.DangerDetector(this)
        }

        detectionJob = scope.launch { captureLoop() }
    }

    internal fun stopDetection() {
        detecting = false
        detectionJob?.cancel()
        dangerDetector?.close()
        dangerDetector = null
        postDetectionChunksLeft = 0
        pendingDetection = null
        updateNotification(false)
    }

    private suspend fun captureLoop() {
        val sampleRate = 16000
        val minBuf = android.media.AudioRecord.getMinBufferSize(
            sampleRate,
            android.media.AudioFormat.CHANNEL_IN_MONO,
            android.media.AudioFormat.ENCODING_PCM_16BIT
        )
        val recorder = android.media.AudioRecord(
            android.media.MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            sampleRate,
            android.media.AudioFormat.CHANNEL_IN_MONO,
            android.media.AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuf, 6400)
        )
        recorder.startRecording()

        val chunk = ByteArray(3200)  // 100ms at 16kHz
        var lastInferenceTime = 0L

        try {
            while (detecting && currentCoroutineContext().isActive) {
                val read = recorder.read(chunk, 0, chunk.size)
                if (read <= 0) continue

                val data = chunk.copyOf(read)
                ringBuffer.write(data)

                // Post-detection countdown
                if (postDetectionChunksLeft > 0) {
                    postDetectionChunksLeft--
                    if (postDetectionChunksLeft == 0) {
                        val det = pendingDetection
                        pendingDetection = null
                        if (det != null) {
                            val pcm = ringBuffer.read(CLIP_BYTES)
                            scope.launch { uploadClipAndAlert(det, pcm) }
                        }
                    }
                }

                // VAD + inference at configured interval
                val now = System.currentTimeMillis()
                if (now - lastInferenceTime >= inferenceIntervalMs) {
                    lastInferenceTime = now
                    val sample = ringBuffer.read(com.formykids.DangerDetector.SAMPLE_SIZE * 2)
                    if (ClipEncoder.calculateRms(sample) > VAD_THRESHOLD && postDetectionChunksLeft == 0) {
                        val detection = dangerDetector?.analyze(sample)
                        if (detection != null) {
                            pendingDetection = detection
                            postDetectionChunksLeft = POST_DETECTION_CHUNKS
                        }
                    }
                }
            }
        } finally {
            recorder.stop()
            recorder.release()
        }
    }

    // uploadClipAndAlert() is added in Task 7
    private suspend fun uploadClipAndAlert(detection: com.formykids.DangerDetector.Detection, pcm: ByteArray) {
        // placeholder — implemented in Task 7
    }
```

- [ ] **Step 2: Build to verify no compilation errors**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```
git add app/app/src/main/java/com/formykids/child/DangerDetectionService.kt
git commit -m "feat: add AudioRecord capture loop with VAD gating and YAMNet inference to DangerDetectionService"
```

---

## Task 7: DangerDetectionService — Clip Pipeline

**Files:**
- Modify: `app/app/src/main/java/com/formykids/child/DangerDetectionService.kt`

- [ ] **Step 1: Add Firebase Storage and OkHttp imports**

At the top of `DangerDetectionService.kt`, add after existing imports:

```kotlin
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.google.firebase.auth.ktx.auth
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
```

- [ ] **Step 2: Add OkHttp client companion field**

In the `companion object` of `DangerDetectionService`, add:

```kotlin
val httpClient = OkHttpClient()
```

- [ ] **Step 3: Replace uploadClipAndAlert() stub with full implementation**

Replace the placeholder `uploadClipAndAlert()`:

```kotlin
    private suspend fun uploadClipAndAlert(
        detection: com.formykids.DangerDetector.Detection,
        pcm: ByteArray
    ) {
        val fid = familyId ?: return
        val alertId = java.util.UUID.randomUUID().toString()
        val clipFile = File(cacheDir, "clips/$alertId.m4a").also { it.parentFile?.mkdirs() }

        try {
            // 1. Encode PCM → AAC
            withContext(Dispatchers.Default) {
                ClipEncoder.encodeToAac(pcm, clipFile)
            }

            // 2. Upload to Firebase Storage
            val storageRef = Firebase.storage.reference.child("clips/$fid/$alertId.m4a")
            storageRef.putFile(android.net.Uri.fromFile(clipFile)).await()
            val clipUrl = storageRef.downloadUrl.await().toString()
            val clipExpiresAt = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000

            // 3. Save alert to Firestore
            FirestoreManager.saveAlert(fid, detection.type, detection.confidence, clipUrl, clipExpiresAt)

            // 4. Trigger FCM via server
            val idToken = Firebase.auth.currentUser?.getIdToken(false)?.await()?.token ?: return
            triggerFcm(idToken, fid, detection.type, detection.confidence)
        } catch (e: Exception) {
            android.util.Log.e("DangerDetection", "Clip pipeline failed: ${e.message}")
        } finally {
            clipFile.delete()
        }
    }

    private suspend fun triggerFcm(idToken: String, familyId: String, type: String, confidence: Float) {
        val prefs = getSharedPreferences(App.PREF_NAME, MODE_PRIVATE)
        val wsUrl = prefs.getString(App.PREF_SERVER_URL, App.DEFAULT_SERVER_URL)!!
        val httpUrl = wsUrl.replaceFirst("wss://", "https://").replaceFirst("ws://", "http://") + "/alert"

        val body = JSONObject().apply {
            put("idToken", idToken)
            put("familyId", familyId)
            put("type", type)
            put("confidence", confidence)
        }.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder().url(httpUrl).post(body).build()
        withContext(Dispatchers.IO) {
            httpClient.newCall(request).execute().close()
        }
    }
```

- [ ] **Step 4: Build to verify no compilation errors**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```
git add app/app/src/main/java/com/formykids/child/DangerDetectionService.kt
git commit -m "feat: implement clip encode/upload/alert pipeline in DangerDetectionService"
```

---

## Task 8: DetectionScheduleReceiver + AlarmManager

**Files:**
- Create: `app/app/src/main/java/com/formykids/child/DetectionScheduleReceiver.kt`

- [ ] **Step 1: Create DetectionScheduleReceiver**

Create `app/app/src/main/java/com/formykids/child/DetectionScheduleReceiver.kt`:

```kotlin
package com.formykids.child

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.Calendar

class DetectionScheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                startDetectionService(context)
                rescheduleAlarms(context, intent.getIntArrayExtra(EXTRA_START_HOURS) ?: intArrayOf(),
                    intent.getIntArrayExtra(EXTRA_END_HOURS) ?: intArrayOf())
            }
            ACTION_START_DETECTION -> {
                val serviceIntent = Intent(context, DangerDetectionService::class.java)
                    .setAction(DangerDetectionService.ACTION_START_DETECTION)
                context.startForegroundService(serviceIntent)
            }
            ACTION_STOP_DETECTION -> {
                val serviceIntent = Intent(context, DangerDetectionService::class.java)
                    .setAction(DangerDetectionService.ACTION_STOP_DETECTION)
                context.startForegroundService(serviceIntent)
            }
        }
    }

    private fun startDetectionService(context: Context) {
        context.startForegroundService(Intent(context, DangerDetectionService::class.java))
    }

    companion object {
        const val ACTION_START_DETECTION = "com.formykids.SCHEDULE_START_DETECTION"
        const val ACTION_STOP_DETECTION = "com.formykids.SCHEDULE_STOP_DETECTION"
        const val EXTRA_START_HOURS = "start_hours"
        const val EXTRA_END_HOURS = "end_hours"

        fun rescheduleAlarms(context: Context, startHours: IntArray, endHours: IntArray) {
            val alarmManager = context.getSystemService(AlarmManager::class.java)

            // Cancel existing
            cancelAlarms(context, alarmManager)

            // Register start alarms
            startHours.forEachIndexed { i, hour ->
                scheduleDaily(context, alarmManager, hour, ACTION_START_DETECTION, 100 + i)
            }
            // Register stop alarms
            endHours.forEachIndexed { i, hour ->
                scheduleDaily(context, alarmManager, hour, ACTION_STOP_DETECTION, 200 + i)
            }
        }

        private fun scheduleDaily(context: Context, alarmManager: AlarmManager, hour: Int, action: String, requestCode: Int) {
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
            }
            val pi = PendingIntent.getBroadcast(
                context, requestCode,
                Intent(context, DetectionScheduleReceiver::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, cal.timeInMillis, AlarmManager.INTERVAL_DAY, pi)
        }

        private fun cancelAlarms(context: Context, alarmManager: AlarmManager) {
            for (requestCode in (100..120) + (200..220)) {
                val pi = PendingIntent.getBroadcast(
                    context, requestCode,
                    Intent(context, DetectionScheduleReceiver::class.java),
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                ) ?: continue
                alarmManager.cancel(pi)
            }
        }
    }
}
```

- [ ] **Step 2: Commit**

```
git add app/app/src/main/java/com/formykids/child/DetectionScheduleReceiver.kt
git commit -m "feat: add DetectionScheduleReceiver for AlarmManager-based schedule control"
```

---

## Task 9: AndroidManifest.xml Updates

**Files:**
- Modify: `app/app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Add permissions after existing permissions block**

Add after `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`:

```xml
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
```

- [ ] **Step 2: Register DangerDetectionService after AudioStreamService entry**

Add after the `AudioStreamService` `<service>` block:

```xml
<service
    android:name=".child.DangerDetectionService"
    android:foregroundServiceType="microphone"
    android:exported="false" />
```

- [ ] **Step 3: Register DetectionScheduleReceiver before closing </application>**

Add before `</application>`:

```xml
<receiver
    android:name=".child.DetectionScheduleReceiver"
    android:exported="false">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
        <action android:name="com.formykids.SCHEDULE_START_DETECTION" />
        <action android:name="com.formykids.SCHEDULE_STOP_DETECTION" />
    </intent-filter>
</receiver>
```

- [ ] **Step 4: Start DangerDetectionService from ChildActivity**

In `child/ChildActivity.kt`, inside `startAudioService()`, add after the existing `startForegroundService` call:

```kotlin
private fun startAudioService() {
    val intent = Intent(this, AudioStreamService::class.java)
    ContextCompat.startForegroundService(this, intent)
    val detectionIntent = Intent(this, DangerDetectionService::class.java)
    ContextCompat.startForegroundService(this, detectionIntent)
}
```

- [ ] **Step 5: Build to verify no compilation errors**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```
git add app/app/src/main/AndroidManifest.xml \
        app/app/src/main/java/com/formykids/child/ChildActivity.kt
git commit -m "feat: register DangerDetectionService and DetectionScheduleReceiver in manifest"
```

---

## Task 10: SettingsActivity — Parent Detection Settings

**Files:**
- Modify: `app/app/src/main/java/com/formykids/settings/SettingsActivity.kt`
- Modify: `app/app/src/main/res/layout/activity_settings.xml`

- [ ] **Step 1: Read current activity_settings.xml**

Read `app/app/src/main/res/layout/activity_settings.xml` to understand current structure.

- [ ] **Step 2: Add detection settings section to layout**

In `activity_settings.xml`, add the following section inside the main `ScrollView`/`LinearLayout`, after the subscription section and before the account section:

```xml
<!-- Detection Settings — shown only for parent role (visibility controlled in code) -->
<LinearLayout
    android:id="@+id/sectionDetection"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="16dp"
    android:visibility="gone">

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="@string/section_detection"
        android:textAppearance="@style/TextAppearance.MaterialComponents.Caption"
        android:textColor="?attr/colorPrimary"
        android:paddingBottom="8dp" />

    <com.google.android.material.card.MaterialCardView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        app:cardCornerRadius="12dp"
        app:cardElevation="2dp">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:padding="16dp">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:gravity="center_vertical">

                <LinearLayout
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:orientation="vertical">

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@string/label_detection_enabled"
                        android:textAppearance="@style/TextAppearance.MaterialComponents.Body1" />

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@string/label_detection_enabled_desc"
                        android:textAppearance="@style/TextAppearance.MaterialComponents.Body2"
                        android:textColor="?android:attr/textColorSecondary" />
                </LinearLayout>

                <androidx.appcompat.widget.SwitchCompat
                    android:id="@+id/switchDetectionEnabled"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content" />
            </LinearLayout>

            <View
                android:layout_width="match_parent"
                android:layout_height="1dp"
                android:background="?android:attr/listDivider"
                android:layout_marginVertical="12dp" />

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/label_detection_schedule"
                android:textAppearance="@style/TextAppearance.MaterialComponents.Body1"
                android:paddingBottom="8dp" />

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:gravity="center_vertical">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/label_start_hour"
                    android:layout_marginEnd="8dp" />

                <NumberPicker
                    android:id="@+id/pickerStartHour"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content" />

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/label_hour_separator"
                    android:layout_marginHorizontal="16dp" />

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/label_end_hour"
                    android:layout_marginEnd="8dp" />

                <NumberPicker
                    android:id="@+id/pickerEndHour"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content" />
            </LinearLayout>

            <com.google.android.material.button.MaterialButton
                android:id="@+id/btnSaveDetectionSchedule"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="12dp"
                android:text="@string/btn_save_detection_schedule" />
        </LinearLayout>
    </com.google.android.material.card.MaterialCardView>
</LinearLayout>
```

- [ ] **Step 3: Update SettingsActivity.kt**

Replace the full `SettingsActivity.kt` content:

```kotlin
package com.formykids.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.NumberPicker
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.formykids.App
import com.formykids.FirestoreManager
import com.formykids.SplashActivity
import com.formykids.child.DetectionScheduleReceiver
import com.formykids.databinding.ActivitySettingsBinding
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private var familyId: String? = null
    private var detectionSettingsListener: com.google.firebase.firestore.ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupPickers()

        binding.btnLogout.setOnClickListener {
            Firebase.auth.signOut()
            getSharedPreferences(App.PREF_NAME, MODE_PRIVATE).edit().clear().apply()
            startActivity(Intent(this, SplashActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        }

        binding.btnManageSubscription.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/account/subscriptions")))
        }

        binding.btnChangePhone.setOnClickListener {
            startActivity(Intent(this, PhoneChangeActivity::class.java))
        }

        binding.btnBack.setOnClickListener { finish() }

        lifecycleScope.launch { loadUserSettings() }
    }

    private fun setupPickers() {
        listOf(binding.pickerStartHour, binding.pickerEndHour).forEach { picker ->
            picker.minValue = 0
            picker.maxValue = 23
            picker.displayedValues = Array(24) { "%02d:00".format(it) }
        }
        binding.pickerStartHour.value = 8
        binding.pickerEndHour.value = 22
    }

    private suspend fun loadUserSettings() {
        val user = FirestoreManager.getCurrentUser() ?: return
        val role = user["role"] as? String ?: return
        val fid = user["familyId"] as? String ?: return
        familyId = fid

        if (role == App.ROLE_PARENT) {
            binding.sectionDetection.visibility = View.VISIBLE
            loadDetectionSettings(fid)
            binding.btnSaveDetectionSchedule.setOnClickListener { saveDetectionSettings(fid) }
        }
    }

    private suspend fun loadDetectionSettings(fid: String) {
        detectionSettingsListener = FirestoreManager.observeDetectionSettings(fid) { enabled, schedule ->
            runOnUiThread {
                binding.switchDetectionEnabled.isChecked = enabled
                schedule.firstOrNull()?.let { (start, end) ->
                    binding.pickerStartHour.value = start
                    binding.pickerEndHour.value = end
                }
            }
        }
    }

    override fun onDestroy() {
        detectionSettingsListener?.remove()
        super.onDestroy()
    }

    private fun saveDetectionSettings(fid: String) {
        val enabled = binding.switchDetectionEnabled.isChecked
        val start = binding.pickerStartHour.value
        val end = binding.pickerEndHour.value
        lifecycleScope.launch {
            FirestoreManager.updateDetectionSettings(fid, enabled, listOf(start to end))
            DetectionScheduleReceiver.rescheduleAlarms(
                this@SettingsActivity,
                intArrayOf(start),
                intArrayOf(end)
            )
        }
    }
}
```

- [ ] **Step 4: Build to verify no errors**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```
git add app/app/src/main/java/com/formykids/settings/SettingsActivity.kt \
        app/app/src/main/res/layout/activity_settings.xml
git commit -m "feat: add detection toggle and schedule settings for parent role in SettingsActivity"
```

---

## Task 11: AlertAdapter — Clip Playback and Download

**Files:**
- Modify: `app/app/src/main/res/layout/item_alert.xml`
- Modify: `app/app/src/main/java/com/formykids/parent/AlertAdapter.kt`

- [ ] **Step 1: Read current item_alert.xml**

Read `app/app/src/main/res/layout/item_alert.xml` to understand current structure.

- [ ] **Step 2: Add play/download buttons to item_alert.xml**

At the bottom of the existing card layout in `item_alert.xml`, add inside the card's inner LinearLayout, after `tvAlertConfidence`:

```xml
<LinearLayout
    android:id="@+id/layoutClipActions"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:layout_marginTop="8dp"
    android:visibility="gone">

    <com.google.android.material.button.MaterialButton
        android:id="@+id/btnPlayClip"
        style="@style/Widget.MaterialComponents.Button.OutlinedButton"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/btn_play_clip"
        android:layout_marginEnd="8dp" />

    <com.google.android.material.button.MaterialButton
        android:id="@+id/btnDownloadClip"
        style="@style/Widget.MaterialComponents.Button.OutlinedButton"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/btn_download_clip" />
</LinearLayout>
```

- [ ] **Step 3: Update AlertAdapter.kt**

Replace the `onBindViewHolder` method in `AlertAdapter.kt`:

```kotlin
override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    val alert = items[position]
    val ts = (alert["timestamp"] as? Long) ?: 0L
    val type = when (alert["type"]) {
        "scream" -> context.getString(R.string.alert_type_scream)
        "cry" -> context.getString(R.string.alert_type_cry)
        else -> context.getString(R.string.alert_type_loud)
    }
    val conf = ((alert["confidence"] as? Double)?.times(100))?.toInt() ?: 0

    holder.binding.tvAlertTime.text = fmt.format(Date(ts))
    holder.binding.tvAlertType.text = type
    holder.binding.tvAlertConfidence.text = context.getString(R.string.alert_confidence_format, conf)

    val chipColor = when (alert["type"]) {
        "scream" -> 0xFFEF4444.toInt()
        "cry" -> 0xFFF59E0B.toInt()
        else -> 0xFF2563EB.toInt()
    }
    (holder.binding.tvAlertType.background.mutate() as? GradientDrawable)?.setColor(chipColor)

    val clipUrl = alert["clipUrl"] as? String
    holder.binding.layoutClipActions.visibility = if (clipUrl != null) View.VISIBLE else View.GONE

    if (clipUrl != null) {
        holder.binding.btnPlayClip.setOnClickListener {
            val player = android.media.MediaPlayer()
            player.setDataSource(clipUrl)
            player.prepareAsync()
            player.setOnPreparedListener { it.start() }
            player.setOnCompletionListener { it.release() }
        }
        holder.binding.btnDownloadClip.setOnClickListener {
            val req = android.app.DownloadManager.Request(android.net.Uri.parse(clipUrl)).apply {
                setTitle("ForMyKids_${fmt.format(Date(ts))}.m4a")
                setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(
                    android.os.Environment.DIRECTORY_DOWNLOADS,
                    "ForMyKids_${ts}.m4a"
                )
            }
            (context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager)
                .enqueue(req)
        }
    }
}
```

Also add this import to `AlertAdapter.kt` at the top:

```kotlin
import android.view.View
```

- [ ] **Step 4: Build to verify**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```
git add app/app/src/main/res/layout/item_alert.xml \
        app/app/src/main/java/com/formykids/parent/AlertAdapter.kt
git commit -m "feat: add clip play and download buttons to AlertAdapter"
```

---

## Task 12: Server /alert Endpoint

**Files:**
- Modify: `server/server.js`
- Modify: `server/fcm.js`

- [ ] **Step 1: Export getMessaging and TYPE_LABELS from fcm.js**

In `server/fcm.js`, add at the top after the existing require:

```javascript
const { getMessaging, getFirestore } = require('./auth');

const TYPE_LABELS = { scream: '비명', cry: '울음', loud: '큰 소리' };
```

And at the bottom, update module.exports:

```javascript
module.exports = { sendDangerAlert, TYPE_LABELS };
```

- [ ] **Step 2: Add /alert endpoint to server.js**

In `server/server.js`, update the import of fcm.js at the top:

```javascript
const { sendDangerAlert, TYPE_LABELS } = require('./fcm');
```

Then add the new endpoint after the `/verify-purchase` route and before `const server = http.createServer(app)`:

```javascript
app.post('/alert', async (req, res) => {
  const { idToken, type, confidence, familyId } = req.body;
  if (!idToken || !type || !familyId) return res.status(400).json({ error: 'missing fields' });
  try {
    const uid = await verifyIdToken(idToken);
    const userDoc = await getFirestore().collection('users').doc(uid).get();
    if (userDoc.data()?.familyId !== familyId) return res.status(403).json({ error: 'forbidden' });
    const subDoc = await getFirestore().collection('subscriptions').doc(uid).get();
    const sub = subDoc.data() ?? {};
    if (sub.plan !== 'premium' || sub.expiresAt <= Date.now()) {
      return res.status(403).json({ error: 'premium required' });
    }
    const parentTokens = await getParentFcmTokens(familyId);
    const typeLabel = TYPE_LABELS[type] ?? type;
    const { getMessaging } = require('./auth');
    await Promise.all(parentTokens.map(token =>
      getMessaging().send({
        token,
        notification: { title: '⚠️ 위험 감지', body: `${typeLabel} 소리가 감지되었습니다` },
        data: { type, confidence: String(confidence), familyId },
        android: { priority: 'high', notification: { channelId: 'danger_alerts' } },
      })
    ));
    res.json({ ok: true });
  } catch (e) {
    console.error('/alert error:', e.message);
    res.status(400).json({ error: e.message });
  }
});
```

- [ ] **Step 3: Verify server starts without errors**

Run: `node server/server.js`
Expected: `Server listening on port 8080` (Ctrl+C to stop)

- [ ] **Step 4: Commit**

```
git add server/server.js server/fcm.js
git commit -m "feat: add POST /alert endpoint to relay server for independent FCM trigger"
```

---

## Task 13: String Resources

**Files:**
- Modify: `app/app/src/main/res/values/strings.xml`

- [ ] **Step 1: Add new strings**

In `strings.xml`, add before `</resources>`:

```xml
<!-- Detection settings -->
<string name="section_detection">감지 설정</string>
<string name="label_detection_enabled">상시 위기 감지</string>
<string name="label_detection_enabled_desc">부모 듣기 없이 아이 폰에서 독립 실행</string>
<string name="label_detection_schedule">감지 시간대</string>
<string name="label_start_hour">시작</string>
<string name="label_end_hour">종료</string>
<string name="label_hour_separator">~</string>
<string name="btn_save_detection_schedule">저장</string>

<!-- DangerDetectionService notifications -->
<string name="detection_active">위기 감지 중</string>
<string name="detection_standby">감지 대기 중</string>

<!-- Alert clip actions -->
<string name="btn_play_clip">재생</string>
<string name="btn_download_clip">다운로드</string>
```

- [ ] **Step 2: Build final verification**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```
git add app/app/src/main/res/values/strings.xml
git commit -m "feat: add string resources for always-on detection feature"
```

---

## Firebase Storage Lifecycle Rule

After deploying, set a 30-day TTL in the Firebase console:

1. Firebase Console → Storage → Rules tab
2. Add lifecycle rule: delete objects in `clips/**` after 30 days
   (Or use `gsutil lifecycle set` with a JSON config if using CLI)

This is a one-time manual step, not automated in this plan.

---

## Testing Checklist

After all tasks complete, verify on device:

- [ ] Child app launches → two foreground notifications visible (AudioStream + DangerDetection)
- [ ] Parent enables detection in Settings → child phone notification changes to "위기 감지 중"
- [ ] Parent sets schedule 08:00–22:00 → detection stops at 22:00, restarts at 08:00
- [ ] Parent disables detection → child notification changes to "감지 대기 중"
- [ ] Simulate danger sound (play scream audio near phone) → parent receives FCM notification
- [ ] Tap alert in history → play button plays clip, download button saves to Downloads
- [ ] Device reboot → detection resumes based on schedule
