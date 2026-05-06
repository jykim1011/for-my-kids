# Volume Amplification & Background Notification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add PCM gain-based volume amplification slider (1x–3x) to the parent app and fix background listening notification visibility on Android 13+ by adding the missing POST_NOTIFICATIONS permission.

**Architecture:** Gain factor stored as `@Volatile var gainFactor` on `AudioPlayer`, set via `AudioListenService.instance?.setGain()`. Notification is already implemented via `startForeground`; only missing `POST_NOTIFICATIONS` permission and runtime request. Notification strings updated to match spec.

**Tech Stack:** Kotlin, Android AudioTrack (PCM ShortArray gain), SeekBar, SharedPreferences, NotificationCompat, ActivityCompat.requestPermissions

---

### Task 1: Add POST_NOTIFICATIONS permission and update notification strings

**Files:**
- Modify: `app/app/src/main/AndroidManifest.xml`
- Modify: `app/app/src/main/res/values/strings.xml`
- Modify: `app/app/src/main/java/com/formykids/parent/AudioListenService.kt`

- [ ] **Step 1: Add POST_NOTIFICATIONS to manifest**

In `app/app/src/main/AndroidManifest.xml`, add after line 13 (after `SCHEDULE_EXACT_ALARM`):
```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

- [ ] **Step 2: Add notification title and content strings**

In `app/app/src/main/res/values/strings.xml`, add before `</resources>`:
```xml
<string name="notification_listening_title">청취 중</string>
<string name="notification_listening_content">아이 소리를 듣고 있습니다</string>
```

- [ ] **Step 3: Update buildNotification() in AudioListenService**

In `app/app/src/main/java/com/formykids/parent/AudioListenService.kt`, in `buildNotification()`, change:
```kotlin
return NotificationCompat.Builder(this, App.NOTIFICATION_CHANNEL_ID)
    .setContentTitle(getString(R.string.app_name))
    .setContentText(getString(R.string.notification_listening))
```
To:
```kotlin
return NotificationCompat.Builder(this, App.NOTIFICATION_CHANNEL_ID)
    .setContentTitle(getString(R.string.notification_listening_title))
    .setContentText(getString(R.string.notification_listening_content))
```

- [ ] **Step 4: Build and verify**

In `app/` directory:
```
./gradlew :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/app/src/main/AndroidManifest.xml app/app/src/main/res/values/strings.xml app/app/src/main/java/com/formykids/parent/AudioListenService.kt
git commit -m "fix: add POST_NOTIFICATIONS permission, update listening notification strings"
```

---

### Task 2: Add PCM gain amplification to AudioPlayer

**Files:**
- Modify: `app/app/src/main/java/com/formykids/parent/AudioPlayer.kt`

- [ ] **Step 1: Add gainFactor property**

In `AudioPlayer.kt`, add after `private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())` (after line 16):
```kotlin
@Volatile var gainFactor: Float = 1.0f
```

- [ ] **Step 2: Apply gain in write()**

In `AudioPlayer.kt`, in `write()`, add gain application after the `if (samples <= 0) return` check and before the `ByteArray` creation. The updated `write()` function:

```kotlin
@Synchronized
fun write(opusBytes: ByteArray) {
    val outShorts = ShortArray(opusFrameSize * 2)
    val samples: Int = try {
        decoder.decode(opusBytes, 0, opusBytes.size, outShorts, 0, opusFrameSize, false)
    } catch (e: Exception) { return }
    if (samples <= 0) return
    if (gainFactor != 1.0f) {
        for (i in 0 until samples) {
            outShorts[i] = (outShorts[i] * gainFactor).toInt().coerceIn(-32767, 32767).toShort()
        }
    }
    val pcmBytes = ByteArray(samples * 2)
    ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(outShorts, 0, samples)
    jitterBuffer.offer(pcmBytes)
}
```

- [ ] **Step 3: Build and verify**

```
./gradlew :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/app/src/main/java/com/formykids/parent/AudioPlayer.kt
git commit -m "feat: add PCM gain amplification to AudioPlayer (1x-3x, clamped)"
```

---

### Task 3: Expose setGain() and instance reference from AudioListenService

**Files:**
- Modify: `app/app/src/main/java/com/formykids/parent/AudioListenService.kt`

- [ ] **Step 1: Add instance to companion object**

In `AudioListenService.kt`, add to the `companion object` block (after `var onVolumeUpdate` on line 29):
```kotlin
@Volatile var instance: AudioListenService? = null
```

- [ ] **Step 2: Add onCreate override to set instance**

Add new `onCreate` override before `onStartCommand`:
```kotlin
override fun onCreate() {
    super.onCreate()
    instance = this
}
```

- [ ] **Step 3: Clear instance in onDestroy**

In `onDestroy()` (currently line 104), add `instance = null` as the first line:
```kotlin
override fun onDestroy() {
    instance = null
    if (isListening) stopListening()
    super.onDestroy()
}
```

- [ ] **Step 4: Add setGain() method**

Add after `routeSpeaker()`:
```kotlin
fun setGain(factor: Float) {
    player?.gainFactor = factor.coerceIn(1.0f, 3.0f)
}
```

- [ ] **Step 5: Build and verify**

```
./gradlew :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add app/app/src/main/java/com/formykids/parent/AudioListenService.kt
git commit -m "feat: expose setGain() and instance ref from AudioListenService"
```

---

### Task 4: Add gain SeekBar to layout and string resources

**Files:**
- Modify: `app/app/src/main/res/layout/activity_parent.xml`
- Modify: `app/app/src/main/res/values/strings.xml`

- [ ] **Step 1: Add string resources**

In `app/app/src/main/res/values/strings.xml`, add before `</resources>`:
```xml
<string name="gain_label">볼륨 증폭</string>
```

- [ ] **Step 2: Add gain card after the volume card**

In `activity_parent.xml`, after the closing `</com.google.android.material.card.MaterialCardView>` of the volume card (after line 249), add:
```xml
<!-- Gain amplification card (visible only when listening) -->
<com.google.android.material.card.MaterialCardView
    android:id="@+id/cardGain"
    style="@style/Widget.ForMyKids.Card"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginBottom="16dp"
    android:visibility="gone">
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="14dp">
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:layout_marginBottom="8dp">
            <TextView
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:text="@string/gain_label"
                android:textColor="@color/text_secondary"
                android:textSize="12sp"
                android:textStyle="bold"/>
            <TextView
                android:id="@+id/tvGainValue"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="1.0x"
                android:textColor="@color/primary"
                android:textSize="12sp"
                android:textStyle="bold"/>
        </LinearLayout>
        <SeekBar
            android:id="@+id/seekBarGain"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:max="20"
            android:progress="0"/>
    </LinearLayout>
</com.google.android.material.card.MaterialCardView>
```

- [ ] **Step 3: Build and verify**

```
./gradlew :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/app/src/main/res/layout/activity_parent.xml app/app/src/main/res/values/strings.xml
git commit -m "feat: add volume gain SeekBar card to ParentActivity layout"
```

---

### Task 5: Wire SeekBar and POST_NOTIFICATIONS request in ParentActivity

**Files:**
- Modify: `app/app/src/main/java/com/formykids/parent/ParentActivity.kt`

- [ ] **Step 1: Add imports**

Add to the import block at the top of `ParentActivity.kt`:
```kotlin
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.SeekBar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
```

- [ ] **Step 2: Add companion object with constants**

Add before `onCreate`:
```kotlin
companion object {
    private const val PREF_GAIN_FACTOR = "pref_gain_factor"
    private const val REQ_NOTIFICATION = 1001
}
```

- [ ] **Step 3: Add helper methods**

Add these three methods to `ParentActivity`:

```kotlin
private fun progressToGainLabel(progress: Int): String {
    val factor = 1.0f + (progress / 20f) * 2.0f
    return String.format("%.1fx", factor)
}

private fun requestNotificationPermissionThenListen() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.requestPermissions(
            this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFICATION
        )
    } else {
        startListeningService()
    }
}

override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    if (requestCode == REQ_NOTIFICATION) {
        startListeningService()
    }
}

private fun startListeningService() {
    startService(Intent(this, AudioListenService::class.java).setAction(AudioListenService.ACTION_START))
    binding.tvListenLabel.text = getString(R.string.listen_stop)
    binding.tvChildStatus.text = getString(R.string.child_status_streaming)
    binding.layoutSpeaker.visibility = View.VISIBLE
    binding.btnSpeaker.setImageResource(R.drawable.ic_volume_up)
    binding.cardGain.visibility = View.VISIBLE
    val prefs = getSharedPreferences(App.PREF_NAME, Context.MODE_PRIVATE)
    val savedProgress = prefs.getInt(PREF_GAIN_FACTOR, 0)
    binding.seekBarGain.progress = savedProgress
    binding.tvGainValue.text = progressToGainLabel(savedProgress)
    setVolumeControlStream(AudioManager.STREAM_MUSIC)
}
```

- [ ] **Step 4: Update btnListen click handler**

In `onCreate`, replace the entire `binding.btnListen.setOnClickListener` block with:
```kotlin
binding.btnListen.setOnClickListener {
    if (AudioListenService.isListening) {
        startService(Intent(this, AudioListenService::class.java).setAction(AudioListenService.ACTION_STOP))
        binding.tvListenLabel.text = getString(R.string.listen_start)
        binding.tvChildStatus.text = getString(R.string.child_status_idle)
        binding.progressVolume.progress = 0
        binding.layoutSpeaker.visibility = View.GONE
        binding.cardGain.visibility = View.GONE
        setVolumeControlStream(AudioManager.USE_DEFAULT_STREAM_TYPE)
    } else {
        requestNotificationPermissionThenListen()
    }
}
```

- [ ] **Step 5: Wire up SeekBar listener in onCreate**

After `binding.btnSpeaker.setOnClickListener { ... }` block, add:
```kotlin
binding.seekBarGain.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        binding.tvGainValue.text = progressToGainLabel(progress)
        val factor = 1.0f + (progress / 20f) * 2.0f
        AudioListenService.instance?.setGain(factor)
    }
    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
    override fun onStopTrackingTouch(seekBar: SeekBar?) {
        val prefs = getSharedPreferences(App.PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(PREF_GAIN_FACTOR, seekBar?.progress ?: 0).apply()
    }
})
```

- [ ] **Step 6: Sync gain card visibility in onResume**

In `onResume`, in the `if (listening)` branch (after `setVolumeControlStream(AudioManager.STREAM_MUSIC)`), add:
```kotlin
binding.cardGain.visibility = View.VISIBLE
val prefs = getSharedPreferences(App.PREF_NAME, Context.MODE_PRIVATE)
val savedProgress = prefs.getInt(PREF_GAIN_FACTOR, 0)
binding.seekBarGain.progress = savedProgress
binding.tvGainValue.text = progressToGainLabel(savedProgress)
```

In the `else` branch (after `setVolumeControlStream(AudioManager.USE_DEFAULT_STREAM_TYPE)`), add:
```kotlin
binding.cardGain.visibility = View.GONE
```

- [ ] **Step 7: Build and verify**

```
./gradlew :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 8: Manual test checklist**

Install debug APK on a physical Android 13+ device and verify:
1. Tap "듣기 시작" → POST_NOTIFICATIONS permission dialog appears (first run only)
2. Grant permission → audio starts, notification appears in status bar: "청취 중 / 아이 소리를 듣고 있습니다"
3. Notification has "듣기 중지" action button
4. Tap notification → ParentActivity opens
5. Volume 증폭 card is visible below 실시간 볼륨 card
6. Drag slider right → volume increases (louder audio)
7. Slider shows "1.0x" to "3.0x" as it moves
8. Press back → notification stays in status bar, audio continues
9. Tap notification "듣기 중지" → audio stops, notification disappears
10. Re-open app → gain slider restores to saved position

- [ ] **Step 9: Commit**

```bash
git add app/app/src/main/java/com/formykids/parent/ParentActivity.kt
git commit -m "feat: wire volume gain slider and POST_NOTIFICATIONS runtime request in ParentActivity"
```
