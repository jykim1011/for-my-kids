# Speakerphone Toggle Design

**Date:** 2026-05-04  
**Feature:** 부모가 아이 오디오 청취 중 스피커폰/이어폰 실시간 전환

---

## Overview

부모가 아이의 소리를 청취할 때 이어폰(수화기)과 스피커폰 사이를 토글 버튼으로 전환할 수 있게 한다. 청취 중일 때만 버튼이 표시되며, 청취 종료 시 시스템 오디오 모드를 자동 복원한다.

---

## Architecture

### Changed Files

| File | Change |
|------|--------|
| `AudioListenService.kt` | AudioManager 모드 관리, speaker action 처리 |
| `ParentActivity.kt` | btnSpeaker UI 연결 (show/hide, icon toggle) |
| `activity_parent.xml` | 스피커 버튼 추가 (초기 GONE) |
| `drawable/ic_volume_off.xml` | 신규 — 이어폰 모드 아이콘 |
| `drawable/ic_volume_up.xml` | 신규 — 스피커폰 모드 아이콘 |
| `values/strings.xml` | `speaker_toggle` 문자열 추가 |

### Not Changed

- `AudioPlayer.kt` — AudioTrack 설정 변경 없음
- `AudioJitterBuffer.kt`, `VolumeAnalyzer.kt` — 변경 없음

---

## Detailed Design

### AudioListenService.kt

**Companion object 상태 추가:**
```kotlin
var isSpeakerphone: Boolean = false
```

**New Intent actions:**
```kotlin
const val ACTION_SPEAKER_ON  = "com.formykids.ACTION_SPEAKER_ON"
const val ACTION_SPEAKER_OFF = "com.formykids.ACTION_SPEAKER_OFF"
```

**startListening() 진입 시:**
```kotlin
val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
am.mode = AudioManager.MODE_IN_COMMUNICATION
am.isSpeakerphoneOn = false   // 기본값: 이어폰
isSpeakerphone = false
```

**ACTION_SPEAKER_ON / ACTION_SPEAKER_OFF 처리:**
```kotlin
ACTION_SPEAKER_ON -> {
    am.isSpeakerphoneOn = true
    isSpeakerphone = true
}
ACTION_SPEAKER_OFF -> {
    am.isSpeakerphoneOn = false
    isSpeakerphone = false
}
```

**stopListening() / onDestroy() 시 복원:**
```kotlin
am.isSpeakerphoneOn = false
am.mode = AudioManager.MODE_NORMAL
isSpeakerphone = false
```

### ParentActivity.kt

**청취 시작 시:**
```kotlin
binding.btnSpeaker.visibility = View.VISIBLE
binding.btnSpeaker.setImageResource(R.drawable.ic_volume_off) // 이어폰 상태
```

**청취 중단 시:**
```kotlin
binding.btnSpeaker.visibility = View.GONE
```

**btnSpeaker 클릭:**
```kotlin
binding.btnSpeaker.setOnClickListener {
    if (AudioListenService.isSpeakerphone) {
        startService(Intent(this, AudioListenService::class.java)
            .setAction(AudioListenService.ACTION_SPEAKER_OFF))
        binding.btnSpeaker.setImageResource(R.drawable.ic_volume_off)
    } else {
        startService(Intent(this, AudioListenService::class.java)
            .setAction(AudioListenService.ACTION_SPEAKER_ON))
        binding.btnSpeaker.setImageResource(R.drawable.ic_volume_up)
    }
}
```

**onResume() 에서 UI 상태 복원:**
```kotlin
if (AudioListenService.isListening) {
    binding.btnSpeaker.visibility = View.VISIBLE
    binding.btnSpeaker.setImageResource(
        if (AudioListenService.isSpeakerphone) R.drawable.ic_volume_up
        else R.drawable.ic_volume_off
    )
} else {
    binding.btnSpeaker.visibility = View.GONE
}
```

### activity_parent.xml

청취 버튼(`btnListen`) 옆에 ImageButton 추가:
```xml
<ImageButton
    android:id="@+id/btnSpeaker"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:src="@drawable/ic_volume_off"
    android:visibility="gone"
    android:contentDescription="@string/speaker_toggle" />
```

---

## Icons Used

새 벡터 드로어블 2개 추가:
- `ic_volume_off.xml` — 이어폰 모드 (기본), Material `volume_off` 아이콘
- `ic_volume_up.xml` — 스피커폰 모드, Material `volume_up` 아이콘

프로젝트의 기존 아이콘(`ic_ear.xml`, `ic_bell.xml` 등)과 동일한 벡터 드로어블 형식으로 작성.

---

## Audio Mode Lifecycle

```
[청취 시작]
  AudioManager.mode = MODE_IN_COMMUNICATION
  isSpeakerphoneOn = false

[스피커 ON]
  isSpeakerphoneOn = true

[스피커 OFF]
  isSpeakerphoneOn = false

[청취 중단 / Service destroy]
  isSpeakerphoneOn = false
  AudioManager.mode = MODE_NORMAL   ← 반드시 복원
```

`MODE_IN_COMMUNICATION`은 시스템 전체 오디오에 영향을 주므로 Service 생명주기 밖으로 절대 누출되어서는 안 된다.

---

## Strings

`strings.xml`에 추가:
```xml
<string name="speaker_toggle">스피커폰 전환</string>
```

---

## Out of Scope

- 기본값을 스피커폰으로 변경하는 설정 항목
- AlertAdapter의 클립 재생에는 스피커폰 토글 미적용 (별개 플로우)
