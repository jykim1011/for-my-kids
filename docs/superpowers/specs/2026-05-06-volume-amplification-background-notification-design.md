# Volume Amplification & Background Notification Design

**Date:** 2026-05-06  
**Scope:** Parent app (AudioListenService, AudioPlayer, ParentActivity)

---

## Overview

Two features for the parent app:
1. **Volume Amplification** — Software PCM gain applied before AudioTrack write, controlled via in-app slider (1.0x~3.0x)
2. **Background Listening Notification** — Persistent foreground service notification with stop action, preventing unnoticed infinite streaming

---

## 1. Volume Amplification

### Architecture

**AudioPlayer.kt**
- Add `var gainFactor: Float = 1.0f` property
- In the PCM write path, multiply each `Short` sample by `gainFactor` and clamp to `±32767` before writing to AudioTrack
- Clamp formula: `(sample * gainFactor).toInt().coerceIn(-32767, 32767).toShort()`

**AudioListenService.kt**
- Add `fun setGain(factor: Float)` method that sets `player?.gainFactor = factor`

**ParentActivity.kt**
- On listening start: activate slider, restore saved gain from SharedPreferences
- On slider change: call service `setGain(factor)`, update label text
- On listening stop: deactivate slider (alpha 0.4, isEnabled = false)
- Persist gain value to SharedPreferences key `pref_gain_factor`

### UI

- `SeekBar` with max = 20 steps (maps to 1.0x~3.0x in 0.1x increments)
- Default position: 0 (= 1.0x, no amplification)
- Label: "볼륨 증폭" with current value displayed (e.g., "1.5x")
- Positioned below the speaker toggle button
- Disabled (alpha 0.4) when not listening

### Mapping

| SeekBar progress | Gain factor |
|-----------------|-------------|
| 0               | 1.0x        |
| 5               | 1.5x        |
| 10              | 2.0x        |
| 15              | 2.5x        |
| 20              | 3.0x        |

Formula: `gainFactor = 1.0f + (progress / 20f) * 2.0f`

---

## 2. Background Listening Notification

### Foreground Service

**AudioListenService.kt**
- On listening start: call `startForeground(NOTIF_ID, buildNotification())`
- On listening stop: call `stopForeground(true)`
- Handle `ACTION_STOP` intent in `onStartCommand` to stop listening from notification

**Notification spec:**
- Channel ID: `channel_listening`
- Channel importance: `IMPORTANCE_LOW` (no sound, no vibration)
- Title: "청취 중"
- Content: "아이 소리를 듣고 있습니다"
- Icon: app icon (or mic icon if available)
- Ongoing: true (cannot be dismissed by swipe)
- Action button: "중지" → fires `ACTION_STOP` PendingIntent to AudioListenService
- Content tap: opens ParentActivity via PendingIntent (`FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE`)

### Manifest

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<service
    android:name=".AudioListenService"
    android:foregroundServiceType="mediaPlayback" />
```

### Runtime Permission (Android 13+)

- Check `POST_NOTIFICATIONS` permission before starting listening
- If not granted, request via `ActivityCompat.requestPermissions`
- If denied, start service without notification (degraded mode — no silent failure)

---

## 3. Data Flow

```
ParentActivity (SeekBar) 
  → AudioListenService.setGain(factor)
  → AudioPlayer.gainFactor = factor
  → PCM samples × gainFactor (clamped) → AudioTrack

ParentActivity (start listening)
  → AudioListenService starts
  → startForeground(notification)

Notification "중지" button
  → ACTION_STOP → AudioListenService.onStartCommand
  → stopListening() → stopForeground(true)
```

---

## 4. Error Handling

- Gain factor validated on set: clamped to `[1.0f, 3.0f]`
- POST_NOTIFICATIONS denied: service starts without notification, no crash
- AudioTrack not initialized: `setGain` is a no-op if `player` is null

---

## 5. Out of Scope

- Speaker volume decrease fix (separate investigation needed)
- Child app volume changes
- Soft clipping / dynamic range compression (not needed at 3x max)
