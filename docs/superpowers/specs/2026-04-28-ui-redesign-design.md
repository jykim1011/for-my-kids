# UI 리디자인 스펙

**작성일:** 2026-04-28
**범위:** 전체 화면 (9개) — POC 수준 레이아웃을 Safe & Trustworthy 디자인 시스템으로 교체

---

## 1. 디자인 방향

**Safe & Trustworthy** — 딥블루 기반, Material You 계열, 부모가 신뢰감을 느끼는 전문적인 앱.

### 색상 팔레트

| 역할 | 색상 | 값 |
|---|---|---|
| Primary | 딥블루 | `#1D4ED8` |
| Primary Light | | `#2563EB` |
| Primary Pale | | `#DBEAFE` |
| Background | 연한 블루 | `#EFF6FF` |
| Surface | 흰색 | `#FFFFFF` |
| On-Primary | 흰색 | `#FFFFFF` |
| Text Primary | | `#1E293B` |
| Text Secondary | | `#64748B` |
| Text Hint | | `#94A3B8` |
| Success | 초록 | `#16A34A` |
| Warning | 앰버 | `#F59E0B` |
| Danger | 빨강 | `#EF4444` |
| Star/Sparkle | 노랑 | `#FCD34D` |

### 공통 스타일

- **버튼 (Primary):** `background=#1D4ED8`, `corner_radius=10dp`, `text=white bold`
- **버튼 (Secondary):** `border=1.5dp #DBEAFE`, `corner_radius=10dp`, `text=#1D4ED8`
- **카드:** `background=white`, `corner_radius=12dp`, `elevation=2dp (shadow)`
- **입력 필드:** `border=1.5dp #DBEAFE`, `corner_radius=10dp`
- **TopBar:** `background=#1D4ED8`, `title=white bold`
- **앱 배경:** `#EFF6FF`

### SVG 아이콘 (커스텀)

모든 아이콘은 이모지 대신 커스텀 SVG 사용.

- **방패 (앱 로고):** 방패 실루엣 + 내부 체크마크, `fill=white`
- **귀 (듣기 버튼):** 입체감 그라디언트 귀 실루엣 + 3단 음파, `fill=radialGradient(white→white 75%)`
- **종 (알림):** 종 실루엣 + 빨간 배지(숫자), `fill=#1D4ED8`, 배지 `fill=#EF4444`
- **톱니 (설정):** 원형 기어, `stroke=white`
- **부모/아이 인물:** 블롭 실루엣 (머리-목-몸 연결), 부모=tall, 아이=부모의 65% 크기, 하트 연결, `fill=linearGradient(#60A5FA→#1D4ED8)`

---

## 2. 화면별 스펙

### 2-1. OnboardingActivity (전화번호 인증)

**구조:**
- 상단 1/3: `#1D4ED8` 배경 + 방패 SVG(48dp) + 앱명 + 슬로건
- 하단 2/3: 흰 배경 영역
  - 섹션 타이틀 "전화번호 인증"
  - 부제목 안내 문구
  - 전화번호 입력 필드 (inputType=phone)
  - Primary 버튼 "인증번호 전송"
  - 인증코드 입력 영역 (초기 gone, 전송 후 visible): 코드 입력 필드 + "확인" 버튼

---

### 2-2. RoleSelectActivity (역할 선택)

**구조:**
- TopBar: "이 기기는 누가 사용하나요?" + 경고 부제목
- 스크롤 없는 두 카드 (선택형):

**부모 카드** (`border=#DBEAFE`, 선택 시 `border=#1D4ED8`):
- SVG: 부모 실루엣(tall) + 아이 실루엣(65% 크기) + 하트
- 타이틀: "부모 기기"
- 설명: "아이 소리를 듣고 알림을 받습니다"

**아이 카드** (`border=#F1F5F9`):
- SVG: 아이 실루엣(단독) + 별 스파클
- 타이틀: "아이 기기"
- 설명: "소리를 감지하여 전송합니다"

---

### 2-3. PairingActivity (기기 페어링)

**구조:**
- TopBar: "기기 연결"
- 부모 플로우:
  - 안내 문구
  - 6자리 코드 카드 (대형 볼드, `letter-spacing=8dp`, `color=#1D4ED8`)
  - 만료 시간 안내
  - 진행 바 (3단계 중 1단계 표시)
  - "연결 완료" 버튼
- 아이 플로우:
  - 안내 문구
  - 코드 입력 필드 (maxLength=6, inputType=number)
  - "연결" 버튼

---

### 2-4. ParentActivity (부모 메인)

**구조:**
- TopBar: 방패 SVG + "for my kids" + 설정 버튼 (톱니 SVG + "설정")
- 상태 카드 2개 (가로 배치):
  - 서버: `●연결됨 (#16A34A)` / `●연결 중단 (#EF4444)`
  - 아이 기기: `○대기 중 (#F59E0B)` / `●스트리밍 중 (#1D4ED8)`
- 듣기 버튼 카드 (`background=gradient #1D4ED8→#2563EB`, `corner_radius=16dp`, `elevation=12dp`):
  - 귀 SVG (56dp)
  - "듣기 시작" / "듣기 중지" (상태 토글)
  - 부제목
- 볼륨 카드:
  - "실시간 볼륨" 레이블 + % 수치
  - `ProgressBar` (height=8dp, track=#DBEAFE, progress=#2563EB)
- 알림 이력 카드:
  - 종 SVG + "알림 이력" + 부제목(감지 건수)
  - 우측 chevron

---

### 2-5. AlertHistoryFragment (알림 이력)

**구조:**
- TopBar 없이 fragment로 삽입
- 섹션 타이틀 "알림 이력"
- `RecyclerView` (기존 ListView 교체):
  - 각 항목: 날짜/시간 + 감지 유형 칩 (비명=red, 울음=amber, 큰소리=blue) + 신뢰도 바
- 비프리미엄: 잠금 상태 카드 + "프리미엄으로 업그레이드" CTA

---

### 2-6. ChildActivity (아이 메인)

**구조:**
- 상단 절반: `gradient #1D4ED8→#1E40AF` 배경
  - 박동 원형 인디케이터 (3중 원: 반투명 → 흰 → 파란 점)
  - "보호 중" 대형 타이틀
  - "부모님이 지켜보고 있어요" 부제목
- 하단: 연결 상태 카드 + 스트리밍 상태 카드 + 설정 버튼

---

### 2-7. PaywallActivity (페이월)

**구조:**
- 상단: `gradient #1E3A8A→#1D4ED8` + 별 아이콘 + "프리미엄으로 업그레이드" + 슬로건
- 기능 목록 카드 (체크 아이콘 + 항목):
  - 무제한 스트리밍
  - AI 위험 감지 알림
  - 알림 이력 30일 보관
  - 오디오 스니펫 저장
- 가격: `₩4,900 / 월` (대형 볼드)
- "구독 시작하기" Primary 버튼
- "닫기" 텍스트 링크

---

### 2-8. SettingsActivity (설정)

**구조:**
- TopBar: back 버튼 + "설정"
- 섹션별 카드 그룹:
  - **서버** 섹션: 서버 URL 표시 + "변경" 링크
  - **구독** 섹션: "구독 관리" 카드 → Play Store 이동
  - **계정** 섹션: "로그아웃" 카드 (red)

---

## 3. 구현 전략

### themes.xml 우선
모든 스타일을 `themes.xml` / `colors.xml` / `styles.xml`에 정의 후 레이아웃에 적용.

```xml
<!-- colors.xml 주요 항목 -->
<color name="primary">#1D4ED8</color>
<color name="primary_light">#2563EB</color>
<color name="primary_pale">#DBEAFE</color>
<color name="background_blue">#EFF6FF</color>
```

### SVG drawable
커스텀 아이콘은 `res/drawable/ic_shield.xml`, `ic_ear.xml`, `ic_bell.xml`, `ic_gear.xml`, `ic_person_parent.xml`, `ic_person_child.xml`로 분리.

### AlertHistoryFragment
기존 `ListView` → `RecyclerView` + 커스텀 어댑터로 교체.

---

## 4. 범위 외

- 애니메이션 (박동 인디케이터 실제 animation은 구현 시 판단)
- 다크 모드 지원
- 태블릿 레이아웃
