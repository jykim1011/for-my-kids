# Always-On Danger Detection — Design Spec

**Date:** 2026-05-03
**Status:** Approved

---

## Overview

Decouple danger detection from the parent's live-listening session. The child's phone runs YAMNet locally at all times (within schedule), sends FCM alerts on detection, and uploads a short audio clip. The parent's live-listen feature continues to work independently and unchanged.

---

## Architecture

Two services coexist independently on the child's phone:

```
Child Phone
├── DangerDetectionService  (NEW)
│   ├── Runs per schedule / manual toggle
│   ├── YAMNet local inference every 2s (VAD-gated)
│   ├── Ring buffer: last 15s of audio
│   └── On detection → clip upload → Firestore alert → FCM
│
└── AudioStreamService  (EXISTING — no changes)
    └── Starts only when parent taps "Listen"
```

---

## Battery Impact

| Mode | Mic | Inference | Network | Est. extra drain |
|---|---|---|---|---|
| Current streaming | always | always | **constant TX** | ~150–200 mA |
| New always-on detection | always | VAD-gated | **event-only** | ~50–80 mA |

Removing continuous network transmission is the dominant saving. On a 3000 mAh battery with normal concurrent phone use, always-on detection is sustainable for a full day.

---

## Battery Optimizations

1. **VAD pre-filter:** calculate RMS of each 1-second audio buffer. If below silence threshold, skip YAMNet inference entirely. Eliminates ~60–70% of inference calls in quiet environments.
2. **2-second inference cycle:** halves CPU load vs current 1-second cycle; worst-case detection latency increases by 1 second.
3. **Low-battery throttle:** when battery ≤ 20%, extend inference cycle from 2s → 5s automatically.

---

## Data Structures

### Firebase Storage
```
clips/{familyId}/{alertId}.aac   (~20 KB per clip, AAC-compressed)
```
- Auto-deleted after 30 days via Firebase Storage lifecycle rule

### Firestore `alerts` collection (extended)
```
alerts/{id}
  ├── familyId        string
  ├── timestamp       long (ms)
  ├── type            "scream" | "cry" | "loud"
  ├── confidence      float 0.0–1.0
  ├── clipUrl         string?   ← NEW: Firebase Storage download URL
  └── clipExpiresAt   long?     ← NEW: timestamp + 30 days
```

### Firestore `families/{familyId}` (extended)
```
  ├── detectionEnabled    boolean
  └── detectionSchedule   [ { startHour: int, endHour: int } ]
```

---

## Clip Recording Flow

```
Ring buffer maintains last 15s of audio (PCM)
    ↓
Danger detected at time T
    ↓
Extract T-5s … T+5s = 10-second window
    ↓
Encode to AAC (~20 KB)
    ↓
Upload to Firebase Storage  →  write clipUrl to Firestore alert
    ↓
Server sends FCM to parent(s)
```

If network is unavailable at detection time, the clip is queued locally and uploaded when connectivity resumes.

---

## Schedule Control Flow

```
Parent changes schedule/toggle in settings
    ↓
Firestore families/{familyId} updated
    ↓
Child DangerDetectionService (always has snapshot listener active)
    ↓
detectionEnabled=true AND current time within schedule → start detection
detectionEnabled=false OR outside schedule → stop detection
```

AlarmManager registers exact alarms for each schedule window start/end. On schedule change, old alarms are cancelled and new ones registered.

---

## Edge Cases

| Situation | Behavior |
|---|---|
| Phone reboot | `BOOT_COMPLETED` receiver re-registers AlarmManager alarms |
| App force-killed | Foreground service — Android restarts it automatically |
| No network at detection | Clip queued locally; uploaded on reconnect |
| Battery ≤ 20% | Inference cycle auto-throttles 2s → 5s |

---

## Parent App Changes

- **SettingsActivity:** add detection toggle + schedule picker (start/end hour)
- **AlertHistoryFragment:** add play button + download button per alert (shown only when `clipUrl` present)
- No changes to live-listen flow

---

## What Does Not Change

- `AudioStreamService` and WebSocket streaming — untouched
- `ParentActivity` live-listen UI — untouched
- Existing `alerts` queries and display logic — extended, not replaced
