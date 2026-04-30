# for-my-kids 앱 고도화 설계

**날짜:** 2026-04-20  
**상태:** 승인됨

---

## 개요

POC 성공 이후 정식 런칭을 위한 고도화 계획. 세 가지 핵심 서브시스템을 추가한다:

1. **AI 소리 위험 감지 + FCM 푸시 알림**
2. **구독 결제 시스템 (Google Play Billing)**
3. **인프라 클라우드 전환 + 앱 구조 고도화**

타겟: 한국 시장 단일. 참고 앱: FindMyKids.

---

## 1. 전체 아키텍처

```
[Child 기기]
  AudioStreamService
    ├─ PCM 스트리밍 → WebSocket → Cloud Run relay
    └─ YAMNet TF Lite (온디바이스 위험 감지)
         └─ 위험 감지 시 → WebSocket danger_alert → Cloud Run
                                                       └─ FCM Admin SDK → Parent 푸시

[Cloud Run relay 서버]
  Express + ws 혼합
  ├─ WebSocket: 오디오 relay, danger_alert 처리
  └─ REST API: 구독 검증, FCM 토큰 등록, 헬스체크

[Firebase]
  ├─ Auth: 전화번호 인증
  ├─ Firestore: 사용자·가족·구독·알림 이력
  ├─ FCM: 푸시 알림
  └─ Cloud Storage: 알림 오디오 스니펫 (프리미엄)

[Google Play Billing]
  구독 구매 → 서버 검증 → Firestore 업데이트
```

### AI 감지 방식: YAMNet (온디바이스)

Claude 등 LLM은 raw 오디오 직접 분석 불가. YAMNet은 Google의 521개 음원 분류 TF Lite 모델(~3.7MB)로 "Crying", "Screaming", "Shout" 등을 실시간 분류한다. 온디바이스이므로 API 비용 없음, 지연 없음.

**감지 파이프라인:**
```
MIC PCM chunk
  → VolumeAnalyzer RMS (1차 필터, 기존 코드 재사용)
    → 임계값 초과 시 2초 버퍼 누적
      → YAMNet 추론 (위험 클래스 확인)
        → danger_alert WebSocket 메시지 → 서버 → FCM
```

---

## 2. 데이터 모델 (Firestore)

```
users/{uid}
  role: "parent" | "child"
  familyId: string
  fcmToken: string
  createdAt: timestamp

families/{familyId}
  parentUids: [uid, ...]
  childUid: string
  pairingCode: string        // 6자리, 10분 만료
  pairingExpiresAt: timestamp

subscriptions/{uid}
  plan: "free" | "premium"
  expiresAt: timestamp
  purchaseToken: string      // Play Billing 검증용
  dailyStreamedSeconds: int  // 무료 제한 추적
  dailyResetAt: timestamp

alerts/{alertId}
  familyId: string
  timestamp: timestamp
  type: "scream" | "cry" | "loud"
  confidence: float
  audioSnippetPath: string   // Cloud Storage (프리미엄만)
```

---

## 3. 인증 및 기기 페어링

**인증:** Firebase 전화번호 인증 (한국 사용자에게 친숙)

**페어링 흐름:**
```
[부모]
  Firebase Auth → 가족 생성 → 6자리 코드 발급 (families 문서 생성, 10분 유효)

[아이 기기]
  Firebase Auth → 6자리 코드 입력 → 서버 검증 → familyId 연결 → 페어링 완료

이후 실행: Auth 상태 확인 → 역할 화면 바로 진입
```

기존 `SharedPreferences` 기반 역할 저장(`App.PREF_ROLE`) 제거. Firebase Auth + Firestore로 대체.

---

## 4. 구독 및 결제

### 플랜 구조

| 항목 | 무료 | 프리미엄 |
|------|------|---------|
| 스트리밍 | 하루 30분 | 무제한 |
| AI 위험 감지 | ✗ | ✓ |
| 알림 이력 | ✗ | 30일 보관 |
| 오디오 스니펫 저장 | ✗ | ✓ |
| 가격 | 0원 | 월 4,900원 (예시) |

### 결제 흐름

```
앱 Paywall 화면
  → Google Play Billing Library v7 (BillingClient)
  → 구매 완료 → purchaseToken
    → Cloud Run POST /verify-purchase
      → Google Play Developer API 검증
        → Firestore subscriptions/{uid} 업데이트
          → 앱 실시간 반영 (Firestore 리스너)
```

### 무료 제한 구현

- 스트리밍 시작 시 서버가 `dailyStreamedSeconds` 확인
- 1800초(30분) 초과 → `stream_limit_reached` WebSocket 메시지
- 앱: 스트리밍 중단 + Paywall 표시
- 매일 자정 `dailyStreamedSeconds` 리셋

**보안 원칙:** 구독 상태 판단은 서버(Firestore)가 권위. 앱 단독 판단 없음.

---

## 5. 앱 화면 구조

```
SplashActivity (Firebase Auth 상태 확인)
  ├─ 미로그인 → OnboardingActivity (전화번호 인증)
  │              → PairingActivity (코드 생성/입력)
  └─ 로그인됨
       ├─ ChildActivity (기존 확장)
       │    └─ AI 감지 상태 표시
       └─ ParentActivity (기존 확장)
            ├─ 알림 이력 탭 (AlertHistoryFragment)
            └─ 구독 상태 배너 + Paywall 진입

SettingsActivity (공통)
  - 서버 주소 설정 (하드코딩 제거)
  - AI 감지 민감도 (낮음/보통/높음, 프리미엄)
  - 역할 변경 / 로그아웃
  - 구독 관리 (Google Play 화면으로 이동)
```

---

## 6. 서버 고도화

### 현재 → 고도화

| 항목 | 현재 | 고도화 |
|------|------|--------|
| 세션 관리 | 글로벌 단일 세션 | familyId 기반 다중 세션 |
| REST API | 없음 | /verify-purchase, /register-token, /health |
| FCM | 없음 | Firebase Admin SDK |
| Firestore | 없음 | alerts 기록, 구독 상태 |
| 인증 | 없음 | Firebase ID Token 검증 |

### 신규 WebSocket 메시지 타입

```
클라이언트 → 서버:
  { type: "auth", idToken: "..." }          // Firebase ID Token
  { type: "danger_alert", class: "scream", confidence: 0.87 }

서버 → 클라이언트:
  { type: "stream_limit_reached" }          // 무료 한도 초과
  { type: "auth_ok", familyId: "..." }
```

---

## 7. 인프라 및 배포

### Cloud Run 구성

```
서비스: family-monitor-relay
리전: asia-northeast3 (서울)
최소 인스턴스: 0 (초기, 사용 없으면 $0)
최대 인스턴스: 10
메모리: 512MB / CPU: 1
포트: 8080
```

최소 인스턴스 0으로 운영 가능한 이유: 앱에 자동 재연결(`scheduleReconnect`) 로직 기존재.  
유료 사용자 확보 후 min-instances=1로 전환하여 안정성 확보.

### 배포 파이프라인

```
GitHub → Cloud Build → Docker 빌드 → Cloud Run 배포
```

기존 `server/Dockerfile` 수정하여 활용.

### 예상 비용

| 단계 | 월 비용 |
|------|---------|
| 초기 (사용자 거의 없음) | ~$0-3 |
| 성장 후 (유료 사용자 확보) | ~$15-25 |

Firebase Spark 무료 플랜으로 시작 → 읽기 5만/일·저장 1GB 초과 시 Blaze 전환.

---

## 8. 구현 순서 (권장)

독립적인 세 서브시스템이므로 아래 순서로 단계적 구현:

1. **인프라 + 서버 고도화** — Cloud Run 배포, familyId 세션, Firebase Admin SDK 통합
2. **인증·페어링** — Firebase Auth, Onboarding/Pairing 화면, Firestore 연동
3. **AI 위험 감지 + FCM** — YAMNet 통합, danger_alert 파이프라인, 푸시 알림
4. **구독·결제** — Google Play Billing, Paywall, 무료 제한 로직
5. **UX 고도화** — Settings, 알림 이력, 프리미엄 UI 처리

---

## 9. 미결정 사항

- 무료 플랜 하루 제한 시간: 30분 (조정 가능)
- 프리미엄 구독 가격: 4,900원/월 (시장 조사 후 확정 권장)
- AI 감지 민감도 기본값: 보통 (YAMNet confidence threshold 0.6 예정)
- 오디오 스니펫 보관 기간: 알림 당 5초, 30일 후 자동 삭제
