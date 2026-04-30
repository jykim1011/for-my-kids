# UI 리디자인 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** POC 수준의 레이아웃 9개를 Safe & Trustworthy(딥블루) 디자인 시스템으로 완전 교체한다.

**Architecture:** themes.xml/colors.xml에 디자인 토큰을 먼저 정의하고, 커스텀 SVG 아이콘을 Vector Drawable로 생성한 뒤, 각 레이아웃 XML을 순서대로 교체한다. AlertHistoryFragment는 ListView → RecyclerView로 함께 교체한다.

**Tech Stack:** Android XML Layouts, Material3, AndroidX RecyclerView, Android Vector Drawable

---

## File Map

| 파일 | 작업 |
|---|---|
| `app/app/src/main/res/values/colors.xml` | 수정 — 디자인 토큰 색상 추가 |
| `app/app/src/main/res/values/themes.xml` | 수정 — Material3 colorPrimary 등 연결 |
| `app/app/src/main/res/values/strings.xml` | 수정 — 이모지 제거, 문자열 정리 |
| `app/app/build.gradle.kts` | 수정 — RecyclerView 의존성 추가 |
| `app/app/src/main/res/drawable/ic_shield.xml` | 생성 — 방패+체크 Vector Drawable |
| `app/app/src/main/res/drawable/ic_ear.xml` | 생성 — 귀+음파 Vector Drawable |
| `app/app/src/main/res/drawable/ic_bell.xml` | 생성 — 종 Vector Drawable |
| `app/app/src/main/res/drawable/ic_gear.xml` | 생성 — 톱니 Vector Drawable |
| `app/app/src/main/res/drawable/ic_person_parent.xml` | 생성 — 부모+아이 실루엣 Vector Drawable |
| `app/app/src/main/res/drawable/ic_person_child.xml` | 생성 — 아이 단독 실루엣 Vector Drawable |
| `app/app/src/main/res/drawable/bg_listen_button.xml` | 생성 — 듣기 버튼 그라디언트 배경 |
| `app/app/src/main/res/drawable/bg_topbar.xml` | 생성 — 상단바 블루 배경 (shape) |
| `app/app/src/main/res/layout/activity_onboarding.xml` | 교체 |
| `app/app/src/main/res/layout/activity_role_select.xml` | 교체 |
| `app/app/src/main/res/layout/activity_pairing.xml` | 교체 |
| `app/app/src/main/res/layout/activity_parent.xml` | 교체 |
| `app/app/src/main/res/layout/activity_child.xml` | 교체 |
| `app/app/src/main/res/layout/activity_paywall.xml` | 교체 |
| `app/app/src/main/res/layout/activity_settings.xml` | 교체 |
| `app/app/src/main/res/layout/fragment_alert_history.xml` | 교체 |
| `app/app/src/main/res/layout/item_alert.xml` | 생성 — RecyclerView 항목 레이아웃 |
| `app/app/src/main/java/com/formykids/parent/AlertAdapter.kt` | 생성 — RecyclerView 어댑터 |
| `app/app/src/main/java/com/formykids/parent/AlertHistoryFragment.kt` | 수정 — ListView → RecyclerView |

---

## Task 1: 디자인 시스템 — 색상·테마·의존성

**Files:**
- Modify: `app/app/src/main/res/values/colors.xml`
- Modify: `app/app/src/main/res/values/themes.xml`
- Modify: `app/app/build.gradle.kts`

- [ ] **Step 1: colors.xml 교체**

`app/app/src/main/res/values/colors.xml` 전체를 다음으로 교체:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="black">#FF000000</color>
    <color name="white">#FFFFFFFF</color>

    <color name="primary">#1D4ED8</color>
    <color name="primary_light">#2563EB</color>
    <color name="primary_pale">#DBEAFE</color>
    <color name="primary_dark">#1E40AF</color>
    <color name="background_blue">#EFF6FF</color>

    <color name="text_primary">#1E293B</color>
    <color name="text_secondary">#64748B</color>
    <color name="text_hint">#94A3B8</color>

    <color name="success">#16A34A</color>
    <color name="warning">#F59E0B</color>
    <color name="danger">#EF4444</color>
    <color name="star">#FCD34D</color>

    <color name="card_border">#E2E8F0</color>
    <color name="divider">#F1F5F9</color>
</resources>
```

- [ ] **Step 2: themes.xml 교체**

`app/app/src/main/res/values/themes.xml` 전체를 다음으로 교체:

```xml
<resources xmlns:tools="http://schemas.android.com/tools">
    <style name="Base.Theme.ForMyKids" parent="Theme.Material3.DayNight.NoActionBar">
        <item name="colorPrimary">@color/primary</item>
        <item name="colorOnPrimary">@color/white</item>
        <item name="colorPrimaryContainer">@color/primary_pale</item>
        <item name="colorSurface">@color/white</item>
        <item name="android:colorBackground">@color/background_blue</item>
        <item name="android:windowBackground">@color/background_blue</item>
    </style>

    <style name="Theme.ForMyKids" parent="Base.Theme.ForMyKids" />

    <!-- TopBar -->
    <style name="Widget.ForMyKids.TopBar">
        <item name="android:background">@color/primary</item>
        <item name="android:paddingStart">20dp</item>
        <item name="android:paddingEnd">20dp</item>
        <item name="android:paddingTop">14dp</item>
        <item name="android:paddingBottom">14dp</item>
    </style>

    <!-- Primary Button -->
    <style name="Widget.ForMyKids.Button.Primary" parent="Widget.Material3.Button">
        <item name="android:backgroundTint">@color/primary</item>
        <item name="cornerRadius">10dp</item>
        <item name="android:textColor">@color/white</item>
        <item name="android:textStyle">bold</item>
    </style>

    <!-- Secondary Button -->
    <style name="Widget.ForMyKids.Button.Secondary" parent="Widget.Material3.Button.OutlinedButton">
        <item name="strokeColor">@color/primary_pale</item>
        <item name="cornerRadius">10dp</item>
        <item name="android:textColor">@color/primary</item>
    </style>

    <!-- Card -->
    <style name="Widget.ForMyKids.Card" parent="Widget.Material3.CardView.Elevated">
        <item name="cardCornerRadius">12dp</item>
        <item name="cardElevation">2dp</item>
        <item name="cardBackgroundColor">@color/white</item>
    </style>

    <!-- Role Card (선택형) -->
    <style name="Widget.ForMyKids.Card.Role" parent="Widget.ForMyKids.Card">
        <item name="cardCornerRadius">14dp</item>
        <item name="cardElevation">4dp</item>
        <item name="strokeColor">@color/primary_pale</item>
        <item name="strokeWidth">2dp</item>
    </style>

    <!-- Input Field -->
    <style name="Widget.ForMyKids.TextInput" parent="Widget.Material3.TextInputLayout.OutlinedBox">
        <item name="boxStrokeColor">@color/primary_pale</item>
        <item name="boxCornerRadiusTopStart">10dp</item>
        <item name="boxCornerRadiusTopEnd">10dp</item>
        <item name="boxCornerRadiusBottomStart">10dp</item>
        <item name="boxCornerRadiusBottomEnd">10dp</item>
    </style>

    <!-- Section Label -->
    <style name="Widget.ForMyKids.SectionLabel">
        <item name="android:textSize">11sp</item>
        <item name="android:textColor">@color/text_hint</item>
        <item name="android:textStyle">bold</item>
        <item name="android:letterSpacing">0.05</item>
        <item name="android:textAllCaps">true</item>
    </style>
</resources>
```

- [ ] **Step 3: RecyclerView 의존성 추가**

`app/app/build.gradle.kts`의 `dependencies` 블록에 추가:

```kotlin
implementation("androidx.recyclerview:recyclerview:1.3.2")
```

- [ ] **Step 4: 빌드 확인**

```bash
cd app && ./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/app/src/main/res/values/colors.xml \
        app/app/src/main/res/values/themes.xml \
        app/app/build.gradle.kts
git commit -m "feat(ui): add design system colors, themes, RecyclerView dep"
```

---

## Task 2: SVG Drawable 아이콘 생성

**Files:**
- Create: `app/app/src/main/res/drawable/ic_shield.xml`
- Create: `app/app/src/main/res/drawable/ic_ear.xml`
- Create: `app/app/src/main/res/drawable/ic_bell.xml`
- Create: `app/app/src/main/res/drawable/ic_gear.xml`
- Create: `app/app/src/main/res/drawable/ic_person_parent.xml`
- Create: `app/app/src/main/res/drawable/ic_person_child.xml`
- Create: `app/app/src/main/res/drawable/bg_listen_button.xml`

- [ ] **Step 1: ic_shield.xml 생성**

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="28dp"
    android:height="28dp"
    android:viewportWidth="28"
    android:viewportHeight="28">
    <path
        android:fillColor="#FFFFFF"
        android:fillAlpha="0.95"
        android:pathData="M14,3L5,7V14C5,18.97 9.02,23.63 14,25C18.98,23.63 23,18.97 23,14V7L14,3Z"/>
    <path
        android:strokeColor="#1D4ED8"
        android:strokeWidth="2"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"
        android:fillColor="#00000000"
        android:pathData="M10,14L13,17L18,11"/>
</vector>
```

- [ ] **Step 2: ic_ear.xml 생성**

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="56dp"
    android:height="56dp"
    android:viewportWidth="56"
    android:viewportHeight="56">
    <!-- Background circle -->
    <path
        android:fillColor="#26FFFFFF"
        android:pathData="M28,2A26,26 0,1,1 28,54A26,26 0,1,1 28,2Z"/>
    <!-- Ear body -->
    <path
        android:fillColor="#FFFFFF"
        android:fillAlpha="0.95"
        android:pathData="M28,11C23,11 16.5,18.5 16.5,28C16.5,36 20,41 23.5,44C25.5,46 26.5,48 26.5,50.5C26.5,52 27.5,53 29,53C30.5,53 31.5,52 31.5,50.5C31.5,47.5 30,44.5 27,42C23.5,39 22,35 22,28C22,22 26,16.5 32,16.5C38,16.5 42,22 42,28C42,31.5 40.5,34.5 37.5,36C34.5,37.5 32.5,39.5 32.5,43C32.5,44.5 33.5,45.5 34.8,45.5C36.1,45.5 37,44.5 37,43C37,41.5 38,40 40.5,38.5C44.5,36.5 47,32.5 47,28C47,18.5 40.5,11 32,11Z"/>
    <!-- Inner ear canal -->
    <path
        android:strokeColor="#3B82F6"
        android:strokeWidth="2.5"
        android:strokeLineCap="round"
        android:fillColor="#00000000"
        android:strokeAlpha="0.6"
        android:pathData="M32,20.5C29,20.5 27,23 27,26.5C27,29.5 28.5,31.5 30.5,33"/>
    <!-- Anti-tragus -->
    <path
        android:fillColor="#FFFFFF"
        android:fillAlpha="0.45"
        android:pathData="M22,34A3,2 0,1,0 28,34A3,2 0,1,0 22,34Z"/>
    <!-- Sound wave 1 -->
    <path
        android:strokeColor="#FFFFFF"
        android:strokeWidth="2.2"
        android:strokeLineCap="round"
        android:fillColor="#00000000"
        android:strokeAlpha="0.6"
        android:pathData="M11,25.5C9.5,27.5 9.5,30.5 11,32.5"/>
    <!-- Sound wave 2 -->
    <path
        android:strokeColor="#FFFFFF"
        android:strokeWidth="2"
        android:strokeLineCap="round"
        android:fillColor="#00000000"
        android:strokeAlpha="0.35"
        android:pathData="M7,21.5C4.5,25 4.5,31 7,34.5"/>
    <!-- Sound wave 3 -->
    <path
        android:strokeColor="#FFFFFF"
        android:strokeWidth="1.8"
        android:strokeLineCap="round"
        android:fillColor="#00000000"
        android:strokeAlpha="0.18"
        android:pathData="M3.5,17.5C0,22 0,34 3.5,38.5"/>
</vector>
```

- [ ] **Step 3: ic_bell.xml 생성**

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="20dp"
    android:height="20dp"
    android:viewportWidth="20"
    android:viewportHeight="20">
    <!-- Bell body -->
    <path
        android:fillColor="#1D4ED8"
        android:fillAlpha="0.9"
        android:pathData="M10,2.5C7.24,2.5 5,4.74 5,7.5V11.5L3.5,13H16.5L15,11.5V7.5C15,4.74 12.76,2.5 10,2.5Z"/>
    <!-- Bell top knob -->
    <path
        android:fillColor="#1D4ED8"
        android:pathData="M10,1.3A1.2,1.2 0,1,0 10,3.7A1.2,1.2 0,1,0 10,1.3Z"/>
    <!-- Clapper -->
    <path
        android:strokeColor="#1D4ED8"
        android:strokeWidth="1.5"
        android:strokeLineCap="round"
        android:fillColor="#00000000"
        android:pathData="M8,13.5C8,14.6 8.9,15.5 10,15.5C11.1,15.5 12,14.6 12,13.5"/>
</vector>
```

- [ ] **Step 4: ic_gear.xml 생성**

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="14dp"
    android:height="14dp"
    android:viewportWidth="14"
    android:viewportHeight="14">
    <path
        android:strokeColor="#FFFFFF"
        android:strokeWidth="1.4"
        android:strokeLineCap="round"
        android:fillColor="#00000000"
        android:pathData="M7,4.5A2.5,2.5 0,1,0 7,9.5A2.5,2.5 0,1,0 7,4.5Z"/>
    <path
        android:strokeColor="#FFFFFF"
        android:strokeWidth="1.4"
        android:strokeLineCap="round"
        android:fillColor="#00000000"
        android:pathData="M7,1.5V2.5M7,11.5V12.5M1.5,7H2.5M11.5,7H12.5M3.4,3.4L4.1,4.1M9.9,9.9L10.6,10.6M10.6,3.4L9.9,4.1M4.1,9.9L3.4,10.6"/>
</vector>
```

- [ ] **Step 5: ic_person_parent.xml 생성 (부모+아이 나란히)**

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="90dp"
    android:height="68dp"
    android:viewportWidth="90"
    android:viewportHeight="68">
    <!-- Adult body (left, tall) - linearGradient 대신 primary_light 단색 사용 -->
    <path
        android:fillColor="#60A5FA"
        android:pathData="M22,4C16,4 11,9 11,15C11,20 14,24 18.5,25.5C17.5,26.5 16,28 14.5,29.5C10,32 5,36 5,44C5,48 5,54 5,54L39,54C39,54 39,48 39,44C39,36 34,32 29.5,29.5C28,28 26.5,26.5 25.5,25.5C30,24 33,20 33,15C33,9 28,4 22,4Z"/>
    <!-- Child body (right, 65% size) -->
    <path
        android:fillColor="#93C5FD"
        android:pathData="M64,18C59.8,18 56.5,21.3 56.5,25.5C56.5,29 58.8,31.8 62,32.8C61.2,33.6 60.2,34.7 59.3,35.8C56.5,37.5 53,40.5 53,46.5C53,49.5 53,54 53,54L75,54C75,54 75,49.5 75,46.5C75,40.5 71.5,37.5 68.7,35.8C67.8,34.7 66.8,33.6 66,32.8C69.2,31.8 71.5,29 71.5,25.5C71.5,21.3 68.2,18 64,18Z"/>
    <!-- Heart between -->
    <path
        android:fillColor="#EF4444"
        android:fillAlpha="0.85"
        android:pathData="M41,36C41,34 42.4,33 43.3,34.5C44.2,33 45.6,34 45.6,36C45.6,38 43.3,40 43.3,40C43.3,40 41,38 41,36Z"/>
</vector>
```

- [ ] **Step 6: ic_person_child.xml 생성 (아이 단독)**

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="90dp"
    android:height="68dp"
    android:viewportWidth="90"
    android:viewportHeight="68">
    <!-- Child body centered -->
    <path
        android:fillColor="#60A5FA"
        android:pathData="M45,10C40.2,10 36.5,13.7 36.5,18.5C36.5,22.5 38.8,25.8 42,27C41.2,27.8 40.2,29 39.2,30.5C36,32.5 31.5,36 31.5,43C31.5,46.5 31.5,52 31.5,52L58.5,52C58.5,52 58.5,46.5 58.5,43C58.5,36 54,32.5 50.8,30.5C49.7,29 48.5,27.8 47.5,27C50.8,25.8 53.5,22.5 53.5,18.5C53.5,13.7 49.8,10 45,10Z"/>
    <!-- Star sparkle left -->
    <path
        android:fillColor="#FCD34D"
        android:fillAlpha="0.9"
        android:pathData="M19,26L20.4,30.2L21.8,26L26,24.6L21.8,23.2L20.4,19L19,23.2L14.8,24.6Z"/>
    <!-- Star sparkle right -->
    <path
        android:fillColor="#FCD34D"
        android:fillAlpha="0.85"
        android:pathData="M68,20L69,23L70,20L73,19L70,18L69,15L68,18L65,19Z"/>
    <!-- Small dot right -->
    <path
        android:fillColor="#FCD34D"
        android:fillAlpha="0.6"
        android:pathData="M72,32A2.5,2.5 0,1,0 72,37A2.5,2.5 0,1,0 72,32Z"/>
</vector>
```

- [ ] **Step 7: bg_listen_button.xml 생성**

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <gradient
        android:startColor="#1D4ED8"
        android:endColor="#2563EB"
        android:angle="135"/>
    <corners android:radius="16dp"/>
</shape>
```

- [ ] **Step 8: 빌드 확인**

```bash
cd app && ./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 9: Commit**

```bash
git add app/app/src/main/res/drawable/
git commit -m "feat(ui): add custom SVG vector drawables"
```

---

## Task 3: OnboardingActivity 레이아웃

**Files:**
- Modify: `app/app/src/main/res/layout/activity_onboarding.xml`

- [ ] **Step 1: activity_onboarding.xml 교체**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="@color/background_blue">

    <!-- 상단 블루 헤더 -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:orientation="vertical"
        android:gravity="center"
        android:background="@color/primary"
        android:paddingTop="48dp"
        android:paddingBottom="32dp">

        <ImageView
            android:layout_width="56dp"
            android:layout_height="56dp"
            android:src="@drawable/ic_shield"
            android:layout_marginBottom="12dp"/>

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/app_name"
            android:textColor="@color/white"
            android:textSize="22sp"
            android:textStyle="bold"/>

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/app_slogan"
            android:textColor="#B3FFFFFF"
            android:textSize="13sp"
            android:layout_marginTop="4dp"/>
    </LinearLayout>

    <!-- 하단 입력 영역 -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="2"
        android:orientation="vertical"
        android:background="@color/white"
        android:padding="24dp">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/phone_auth_title"
            android:textColor="@color/text_primary"
            android:textSize="20sp"
            android:textStyle="bold"
            android:layout_marginBottom="4dp"/>

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/phone_auth_desc"
            android:textColor="@color/text_secondary"
            android:textSize="13sp"
            android:layout_marginBottom="24dp"/>

        <com.google.android.material.textfield.TextInputLayout
            android:id="@+id/tilPhone"
            style="@style/Widget.ForMyKids.TextInput"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:hint="@string/phone_hint"
            android:layout_marginBottom="12dp">

            <com.google.android.material.textfield.TextInputEditText
                android:id="@+id/etPhone"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:inputType="phone"/>
        </com.google.android.material.textfield.TextInputLayout>

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btnSendCode"
            style="@style/Widget.ForMyKids.Button.Primary"
            android:layout_width="match_parent"
            android:layout_height="52dp"
            android:text="@string/send_code"
            android:layout_marginBottom="20dp"/>

        <!-- 인증번호 입력 (초기 GONE) -->
        <LinearLayout
            android:id="@+id/layoutCodeEntry"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:visibility="gone">

            <com.google.android.material.textfield.TextInputLayout
                android:id="@+id/tilCode"
                style="@style/Widget.ForMyKids.TextInput"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:hint="@string/code_hint"
                android:layout_marginBottom="12dp">

                <com.google.android.material.textfield.TextInputEditText
                    android:id="@+id/etCode"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:inputType="number"
                    android:maxLength="6"/>
            </com.google.android.material.textfield.TextInputLayout>

            <com.google.android.material.button.MaterialButton
                android:id="@+id/btnVerify"
                style="@style/Widget.ForMyKids.Button.Primary"
                android:layout_width="match_parent"
                android:layout_height="52dp"
                android:text="@string/verify"/>
        </LinearLayout>
    </LinearLayout>

</LinearLayout>
```

- [ ] **Step 2: strings.xml에 신규 문자열 추가**

`app/app/src/main/res/values/strings.xml`에 다음 항목 추가:

```xml
<string name="app_slogan">아이를 안전하게 지켜드립니다</string>
<string name="phone_auth_title">전화번호 인증</string>
<string name="phone_auth_desc">본인 확인을 위해 전화번호를 입력해 주세요</string>
<string name="phone_hint">010-0000-0000</string>
<string name="send_code">인증번호 전송</string>
<string name="code_hint">인증번호 6자리</string>
<string name="verify">확인</string>
```

기존 이모지 포함 문자열도 교체:
```xml
<!-- 기존: <string name="status_connected">서버: 연결됨 🟢</string> -->
<string name="status_connected">연결됨</string>
<!-- 기존: <string name="status_disconnected">서버: 연결 중... 🔴</string> -->
<string name="status_disconnected">연결 중단</string>
<!-- 기존: <string name="protecting">보호 중 🛡️</string> -->
<string name="protecting">보호 중</string>
<!-- 기존: <string name="notification_text">보호 중 🛡️</string> -->
<string name="notification_text">보호 중</string>
<!-- 기존: <string name="other_parent_listening">상대방도 듣는 중 👂</string> -->
<string name="other_parent_listening">다른 보호자도 듣는 중</string>
<!-- 신규 -->
<string name="server_label">서버</string>
<string name="child_device_label">아이 기기</string>
<string name="streaming">전송 중</string>
<string name="idle">대기 중</string>
<string name="volume_label">실시간 볼륨</string>
<string name="alert_history">알림 이력</string>
<string name="settings">설정</string>
<string name="protecting_subtitle">부모님이 지켜보고 있어요</string>
<string name="server_connection">서버 연결</string>
<string name="sound_sending">소리 전송</string>
```

- [ ] **Step 3: 빌드 확인**

```bash
cd app && ./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/app/src/main/res/layout/activity_onboarding.xml \
        app/app/src/main/res/values/strings.xml
git commit -m "feat(ui): redesign OnboardingActivity with Safe & Trustworthy theme"
```

---

## Task 4: RoleSelectActivity 레이아웃

**Files:**
- Modify: `app/app/src/main/res/layout/activity_role_select.xml`

- [ ] **Step 1: activity_role_select.xml 교체**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="@color/background_blue">

    <!-- TopBar -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:background="@color/primary"
        android:orientation="vertical"
        android:paddingStart="20dp"
        android:paddingEnd="20dp"
        android:paddingTop="20dp"
        android:paddingBottom="16dp">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="이 기기는 누가 사용하나요?"
            android:textColor="@color/white"
            android:textSize="16sp"
            android:textStyle="bold"/>

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="한 번 설정하면 변경이 어렵습니다"
            android:textColor="#99FFFFFF"
            android:textSize="12sp"
            android:layout_marginTop="4dp"/>
    </LinearLayout>

    <!-- 카드 영역 -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="vertical"
        android:padding="20dp"
        android:gravity="center_vertical">

        <!-- 부모 카드 -->
        <com.google.android.material.card.MaterialCardView
            android:id="@+id/btnParent"
            style="@style/Widget.ForMyKids.Card.Role"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="16dp"
            android:clickable="true"
            android:focusable="true">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:gravity="center"
                android:padding="20dp">

                <ImageView
                    android:layout_width="90dp"
                    android:layout_height="68dp"
                    android:src="@drawable/ic_person_parent"
                    android:layout_marginBottom="12dp"
                    android:scaleType="fitCenter"/>

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="부모 기기"
                    android:textColor="@color/text_primary"
                    android:textSize="15sp"
                    android:textStyle="bold"
                    android:layout_marginBottom="4dp"/>

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="아이 소리를 듣고 알림을 받습니다"
                    android:textColor="@color/text_secondary"
                    android:textSize="12sp"/>
            </LinearLayout>
        </com.google.android.material.card.MaterialCardView>

        <!-- 아이 카드 -->
        <com.google.android.material.card.MaterialCardView
            android:id="@+id/btnChild"
            style="@style/Widget.ForMyKids.Card"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:clickable="true"
            android:focusable="true">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:gravity="center"
                android:padding="20dp">

                <ImageView
                    android:layout_width="90dp"
                    android:layout_height="68dp"
                    android:src="@drawable/ic_person_child"
                    android:layout_marginBottom="12dp"
                    android:scaleType="fitCenter"/>

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="아이 기기"
                    android:textColor="@color/text_primary"
                    android:textSize="15sp"
                    android:textStyle="bold"
                    android:layout_marginBottom="4dp"/>

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="소리를 감지하여 전송합니다"
                    android:textColor="@color/text_secondary"
                    android:textSize="12sp"/>
            </LinearLayout>
        </com.google.android.material.card.MaterialCardView>

    </LinearLayout>
</LinearLayout>
```

- [ ] **Step 2: RoleSelectActivity.kt — MaterialCardView 클릭 확인**

`app/app/src/main/java/com/formykids/auth/RoleSelectActivity.kt`를 열어 `btnParent`, `btnChild` ID가 기존 코드에서 그대로 사용되는지 확인한다. 레이아웃에서 ID가 `MaterialCardView`로 이동했으므로 `binding.btnParent.setOnClickListener` 호출이 그대로 동작한다 (ViewBinding이 ID 기반이므로 타입 변환 불필요).

- [ ] **Step 3: 빌드 확인**

```bash
cd app && ./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/app/src/main/res/layout/activity_role_select.xml
git commit -m "feat(ui): redesign RoleSelectActivity with person illustrations"
```

---

## Task 5: PairingActivity 레이아웃

**Files:**
- Modify: `app/app/src/main/res/layout/activity_pairing.xml`

- [ ] **Step 1: activity_pairing.xml 교체**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="@color/background_blue">

    <!-- TopBar -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:background="@color/primary"
        android:paddingStart="20dp"
        android:paddingEnd="20dp"
        android:paddingTop="16dp"
        android:paddingBottom="16dp">
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="기기 연결"
            android:textColor="@color/white"
            android:textSize="16sp"
            android:textStyle="bold"/>
    </LinearLayout>

    <!-- Parent flow -->
    <LinearLayout
        android:id="@+id/layoutParent"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="vertical"
        android:gravity="center"
        android:padding="24dp"
        android:visibility="gone">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="아이 기기에 이 코드를 입력하세요"
            android:textColor="@color/text_secondary"
            android:textSize="14sp"
            android:layout_marginBottom="20dp"/>

        <com.google.android.material.card.MaterialCardView
            style="@style/Widget.ForMyKids.Card"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="24dp"
            app:cardElevation="4dp">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:gravity="center"
                android:padding="24dp">

                <TextView
                    android:id="@+id/tvPairingCode"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:textSize="42sp"
                    android:textStyle="bold"
                    android:letterSpacing="0.3"
                    android:textColor="@color/primary"
                    android:layout_marginBottom="8dp"/>

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="코드는 5분 후 만료됩니다"
                    android:textColor="@color/text_hint"
                    android:textSize="11sp"/>
            </LinearLayout>
        </com.google.android.material.card.MaterialCardView>

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btnCodeDone"
            style="@style/Widget.ForMyKids.Button.Primary"
            android:layout_width="match_parent"
            android:layout_height="52dp"
            android:text="연결 완료"/>
    </LinearLayout>

    <!-- Child flow -->
    <LinearLayout
        android:id="@+id/layoutChild"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="vertical"
        android:gravity="center"
        android:padding="24dp"
        android:visibility="gone">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="부모 기기에서 받은 6자리 코드를 입력하세요"
            android:textColor="@color/text_secondary"
            android:textSize="14sp"
            android:gravity="center"
            android:layout_marginBottom="24dp"/>

        <com.google.android.material.textfield.TextInputLayout
            style="@style/Widget.ForMyKids.TextInput"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:hint="6자리 코드"
            android:layout_marginBottom="16dp">

            <com.google.android.material.textfield.TextInputEditText
                android:id="@+id/etCode"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:inputType="number"
                android:maxLength="6"/>
        </com.google.android.material.textfield.TextInputLayout>

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btnSubmitCode"
            style="@style/Widget.ForMyKids.Button.Primary"
            android:layout_width="match_parent"
            android:layout_height="52dp"
            android:text="연결"/>
    </LinearLayout>

</LinearLayout>
```

- [ ] **Step 2: 빌드 확인**

```bash
cd app && ./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/app/src/main/res/layout/activity_pairing.xml
git commit -m "feat(ui): redesign PairingActivity"
```

---

## Task 6: ParentActivity 레이아웃

**Files:**
- Modify: `app/app/src/main/res/layout/activity_parent.xml`

- [ ] **Step 1: activity_parent.xml 교체**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="@color/background_blue">

    <!-- TopBar -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:background="@color/primary"
        android:paddingStart="20dp"
        android:paddingEnd="16dp"
        android:paddingTop="14dp"
        android:paddingBottom="14dp"
        android:orientation="horizontal"
        android:gravity="center_vertical">

        <ImageView
            android:layout_width="28dp"
            android:layout_height="28dp"
            android:src="@drawable/ic_shield"
            android:layout_marginEnd="10dp"/>

        <TextView
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="@string/app_name"
            android:textColor="@color/white"
            android:textSize="16sp"
            android:textStyle="bold"/>

        <LinearLayout
            android:id="@+id/btnSettings"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:background="@drawable/bg_settings_pill"
            android:paddingStart="12dp"
            android:paddingEnd="12dp"
            android:paddingTop="5dp"
            android:paddingBottom="5dp"
            android:orientation="horizontal"
            android:gravity="center_vertical"
            android:clickable="true"
            android:focusable="true">

            <ImageView
                android:layout_width="14dp"
                android:layout_height="14dp"
                android:src="@drawable/ic_gear"
                android:layout_marginEnd="4dp"/>

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/settings"
                android:textColor="@color/white"
                android:textSize="12sp"/>
        </LinearLayout>
    </LinearLayout>

    <!-- Content -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="vertical"
        android:padding="20dp">

        <!-- Status row -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:layout_marginBottom="20dp">

            <com.google.android.material.card.MaterialCardView
                style="@style/Widget.ForMyKids.Card"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:layout_marginEnd="8dp">
                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical"
                    android:padding="12dp">
                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@string/server_label"
                        android:textColor="@color/text_secondary"
                        android:textSize="11sp"
                        android:textStyle="bold"
                        android:layout_marginBottom="4dp"/>
                    <TextView
                        android:id="@+id/tvServerStatus"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@string/status_disconnected"
                        android:textColor="@color/warning"
                        android:textSize="13sp"
                        android:textStyle="bold"/>
                </LinearLayout>
            </com.google.android.material.card.MaterialCardView>

            <com.google.android.material.card.MaterialCardView
                style="@style/Widget.ForMyKids.Card"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1">
                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical"
                    android:padding="12dp">
                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@string/child_device_label"
                        android:textColor="@color/text_secondary"
                        android:textSize="11sp"
                        android:textStyle="bold"
                        android:layout_marginBottom="4dp"/>
                    <TextView
                        android:id="@+id/tvChildStatus"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@string/child_status_idle"
                        android:textColor="@color/warning"
                        android:textSize="13sp"
                        android:textStyle="bold"/>
                </LinearLayout>
            </com.google.android.material.card.MaterialCardView>
        </LinearLayout>

        <!-- Listen button card -->
        <LinearLayout
            android:id="@+id/btnListen"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:background="@drawable/bg_listen_button"
            android:orientation="vertical"
            android:gravity="center"
            android:padding="24dp"
            android:layout_marginBottom="20dp"
            android:clickable="true"
            android:focusable="true"
            android:elevation="6dp">

            <ImageView
                android:layout_width="56dp"
                android:layout_height="56dp"
                android:src="@drawable/ic_ear"
                android:layout_marginBottom="10dp"/>

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/listen_start"
                android:textColor="@color/white"
                android:textSize="18sp"
                android:textStyle="bold"/>

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="탭하여 아이 소리 듣기"
                android:textColor="#B3FFFFFF"
                android:textSize="12sp"
                android:layout_marginTop="4dp"/>
        </LinearLayout>

        <!-- Volume card -->
        <com.google.android.material.card.MaterialCardView
            style="@style/Widget.ForMyKids.Card"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="16dp">
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
                        android:text="@string/volume_label"
                        android:textColor="@color/text_secondary"
                        android:textSize="12sp"
                        android:textStyle="bold"/>
                    <TextView
                        android:id="@+id/tvVolumePercent"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="0%"
                        android:textColor="@color/primary"
                        android:textSize="12sp"
                        android:textStyle="bold"/>
                </LinearLayout>
                <ProgressBar
                    android:id="@+id/progressVolume"
                    style="@android:style/Widget.ProgressBar.Horizontal"
                    android:layout_width="match_parent"
                    android:layout_height="8dp"
                    android:max="100"
                    android:progress="0"
                    android:progressTint="@color/primary_light"
                    android:progressBackgroundTint="@color/primary_pale"/>
            </LinearLayout>
        </com.google.android.material.card.MaterialCardView>

        <!-- Alert history card -->
        <com.google.android.material.card.MaterialCardView
            android:id="@+id/btnAlertHistory"
            style="@style/Widget.ForMyKids.Card"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:clickable="true"
            android:focusable="true">
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:padding="14dp"
                android:gravity="center_vertical">

                <LinearLayout
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:background="@drawable/bg_icon_circle"
                    android:padding="8dp"
                    android:layout_marginEnd="12dp">
                    <ImageView
                        android:layout_width="20dp"
                        android:layout_height="20dp"
                        android:src="@drawable/ic_bell"/>
                </LinearLayout>

                <LinearLayout
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:orientation="vertical">
                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@string/alert_history"
                        android:textColor="@color/text_primary"
                        android:textSize="13sp"
                        android:textStyle="bold"/>
                    <TextView
                        android:id="@+id/tvOtherParent"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@string/other_parent_listening"
                        android:textColor="@color/text_hint"
                        android:textSize="11sp"
                        android:visibility="gone"/>
                </LinearLayout>

                <ImageView
                    android:layout_width="16dp"
                    android:layout_height="16dp"
                    android:src="@drawable/ic_chevron_right"/>
            </LinearLayout>
        </com.google.android.material.card.MaterialCardView>

        <FrameLayout
            android:id="@+id/fragmentContainer"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"/>

    </LinearLayout>
</LinearLayout>
```

- [ ] **Step 2: 추가 drawable 생성**

`app/app/src/main/res/drawable/bg_settings_pill.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#26FFFFFF"/>
    <corners android:radius="20dp"/>
</shape>
```

`app/app/src/main/res/drawable/bg_icon_circle.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="@color/background_blue"/>
    <corners android:radius="10dp"/>
</shape>
```

`app/app/src/main/res/drawable/ic_chevron_right.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="16dp"
    android:height="16dp"
    android:viewportWidth="16"
    android:viewportHeight="16">
    <path
        android:strokeColor="#1D4ED8"
        android:strokeWidth="2"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"
        android:fillColor="#00000000"
        android:pathData="M6,4L10,8L6,12"/>
</vector>
```

- [ ] **Step 3: ParentActivity.kt — btnListen ID 타입 수정**

`ParentActivity.kt`에서 `binding.btnListen.text = ...` 호출이 있다. 레이아웃에서 `btnListen`이 이제 `LinearLayout`이므로 `TextView`를 별도 ID로 분리한다.

`activity_parent.xml`의 `btnListen` 내부 첫 번째 `TextView`에 ID 추가:
```xml
<TextView
    android:id="@+id/tvListenLabel"
    ...
    android:text="@string/listen_start"
    .../>
```

`ParentActivity.kt`에서 변경:
```kotlin
// 기존
binding.btnListen.text = getString(R.string.listen_stop)
// 변경
binding.tvListenLabel.text = getString(R.string.listen_stop)

// 기존
binding.btnListen.text = getString(R.string.listen_start)
// 변경
binding.tvListenLabel.text = getString(R.string.listen_start)
```

- [ ] **Step 4: 빌드 확인**

```bash
cd app && ./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/app/src/main/res/layout/activity_parent.xml \
        app/app/src/main/res/drawable/bg_settings_pill.xml \
        app/app/src/main/res/drawable/bg_icon_circle.xml \
        app/app/src/main/res/drawable/ic_chevron_right.xml \
        app/app/src/main/java/com/formykids/parent/ParentActivity.kt
git commit -m "feat(ui): redesign ParentActivity with ear icon and status cards"
```

---

## Task 7: ChildActivity 레이아웃

**Files:**
- Modify: `app/app/src/main/res/layout/activity_child.xml`

- [ ] **Step 1: activity_child.xml 교체**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">

    <!-- 상단 블루 풀배경 -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:background="@drawable/bg_child_header"
        android:orientation="vertical"
        android:gravity="center">

        <!-- 박동 원형 인디케이터 -->
        <FrameLayout
            android:layout_width="80dp"
            android:layout_height="80dp"
            android:layout_marginBottom="16dp">
            <!-- Outer ring -->
            <View
                android:layout_width="80dp"
                android:layout_height="80dp"
                android:background="@drawable/bg_pulse_outer"
                android:layout_gravity="center"/>
            <!-- Middle ring -->
            <View
                android:layout_width="60dp"
                android:layout_height="60dp"
                android:background="@drawable/bg_pulse_middle"
                android:layout_gravity="center"/>
            <!-- Inner dot -->
            <View
                android:layout_width="20dp"
                android:layout_height="20dp"
                android:background="@drawable/bg_pulse_inner"
                android:layout_gravity="center"/>
        </FrameLayout>

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/protecting"
            android:textColor="@color/white"
            android:textSize="26sp"
            android:textStyle="bold"
            android:layout_marginBottom="6dp"/>

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/protecting_subtitle"
            android:textColor="#B3FFFFFF"
            android:textSize="13sp"/>
    </LinearLayout>

    <!-- 하단 상태 카드 영역 -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:orientation="vertical"
        android:background="@color/background_blue"
        android:padding="20dp">

        <com.google.android.material.card.MaterialCardView
            style="@style/Widget.ForMyKids.Card"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="12dp">
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:padding="14dp">
                <TextView
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="@string/server_connection"
                    android:textColor="@color/text_secondary"
                    android:textSize="12sp"
                    android:textStyle="bold"/>
                <TextView
                    android:id="@+id/tvServerStatus"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/status_disconnected"
                    android:textColor="@color/warning"
                    android:textSize="12sp"
                    android:textStyle="bold"/>
            </LinearLayout>
        </com.google.android.material.card.MaterialCardView>

        <com.google.android.material.card.MaterialCardView
            style="@style/Widget.ForMyKids.Card"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="20dp">
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:padding="14dp">
                <TextView
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="@string/sound_sending"
                    android:textColor="@color/text_secondary"
                    android:textSize="12sp"
                    android:textStyle="bold"/>
                <TextView
                    android:id="@+id/tvStreamStatus"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/idle"
                    android:textColor="@color/text_hint"
                    android:textSize="12sp"
                    android:textStyle="bold"/>
            </LinearLayout>
        </com.google.android.material.card.MaterialCardView>

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btnSettings"
            style="@style/Widget.ForMyKids.Button.Secondary"
            android:layout_width="match_parent"
            android:layout_height="48dp"
            android:text="@string/settings"/>

    </LinearLayout>
</LinearLayout>
```

- [ ] **Step 2: 펄스 drawable 생성**

`app/app/src/main/res/drawable/bg_child_header.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <gradient
        android:startColor="#1D4ED8"
        android:endColor="#1E40AF"
        android:angle="270"/>
</shape>
```

`app/app/src/main/res/drawable/bg_pulse_outer.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="oval">
    <solid android:color="#26FFFFFF"/>
</shape>
```

`app/app/src/main/res/drawable/bg_pulse_middle.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="oval">
    <solid android:color="#E6FFFFFF"/>
</shape>
```

`app/app/src/main/res/drawable/bg_pulse_inner.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="oval">
    <solid android:color="#1D4ED8"/>
</shape>
```

- [ ] **Step 3: ChildActivity.kt — tvStreamStatus 문자열 수정**

`ChildActivity.kt`에서 `"스트리밍: 전송 중 🎙️"` → `getString(R.string.streaming)`, `"스트리밍: 대기 중"` → `getString(R.string.idle)` 로 변경:

```kotlin
AudioStreamService.statusCallback = { isStreaming ->
    runOnUiThread {
        binding.tvStreamStatus.text =
            if (isStreaming) getString(R.string.streaming) else getString(R.string.idle)
    }
}
```

- [ ] **Step 4: 빌드 확인**

```bash
cd app && ./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/app/src/main/res/layout/activity_child.xml \
        app/app/src/main/res/drawable/bg_child_header.xml \
        app/app/src/main/res/drawable/bg_pulse_outer.xml \
        app/app/src/main/res/drawable/bg_pulse_middle.xml \
        app/app/src/main/res/drawable/bg_pulse_inner.xml \
        app/app/src/main/java/com/formykids/child/ChildActivity.kt
git commit -m "feat(ui): redesign ChildActivity with pulse indicator"
```

---

## Task 8: PaywallActivity 레이아웃

**Files:**
- Modify: `app/app/src/main/res/layout/activity_paywall.xml`

- [ ] **Step 1: activity_paywall.xml 교체**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">

    <!-- 상단 헤더 -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:background="@drawable/bg_paywall_header"
        android:orientation="vertical"
        android:gravity="center"
        android:paddingTop="36dp"
        android:paddingBottom="28dp"
        android:paddingStart="24dp"
        android:paddingEnd="24dp">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="⭐"
            android:textSize="28sp"
            android:layout_marginBottom="8dp"/>

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="프리미엄으로 업그레이드"
            android:textColor="@color/white"
            android:textSize="18sp"
            android:textStyle="bold"
            android:layout_marginBottom="6dp"/>

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="무제한 모니터링을 시작하세요"
            android:textColor="#B3FFFFFF"
            android:textSize="13sp"/>
    </LinearLayout>

    <!-- 기능 목록 + 가격 + 버튼 -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="vertical"
        android:background="@color/background_blue"
        android:padding="20dp">

        <com.google.android.material.card.MaterialCardView
            style="@style/Widget.ForMyKids.Card"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="20dp">
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:padding="4dp">

                <include layout="@layout/item_feature_row" android:id="@+id/row1"/>
                <View android:layout_width="match_parent" android:layout_height="1dp" android:background="@color/divider"/>
                <include layout="@layout/item_feature_row" android:id="@+id/row2"/>
                <View android:layout_width="match_parent" android:layout_height="1dp" android:background="@color/divider"/>
                <include layout="@layout/item_feature_row" android:id="@+id/row3"/>
                <View android:layout_width="match_parent" android:layout_height="1dp" android:background="@color/divider"/>
                <include layout="@layout/item_feature_row" android:id="@+id/row4"/>
            </LinearLayout>
        </com.google.android.material.card.MaterialCardView>

        <LinearLayout
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:gravity="bottom"
            android:layout_gravity="center_horizontal"
            android:layout_marginBottom="24dp">
            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="₩4,900"
                android:textColor="@color/primary"
                android:textSize="28sp"
                android:textStyle="bold"/>
            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text=" / 월"
                android:textColor="@color/text_secondary"
                android:textSize="14sp"
                android:paddingBottom="4dp"/>
        </LinearLayout>

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btnSubscribe"
            style="@style/Widget.ForMyKids.Button.Primary"
            android:layout_width="match_parent"
            android:layout_height="52dp"
            android:text="구독 시작하기"
            android:enabled="false"
            android:layout_marginBottom="12dp"/>

        <TextView
            android:id="@+id/btnClose"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="닫기"
            android:textColor="@color/text_hint"
            android:textSize="13sp"
            android:padding="8dp"
            android:layout_gravity="center_horizontal"
            android:clickable="true"
            android:focusable="true"/>
    </LinearLayout>
</LinearLayout>
```

- [ ] **Step 2: item_feature_row.xml 생성**

`app/app/src/main/res/layout/item_feature_row.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:gravity="center_vertical"
    android:paddingStart="14dp"
    android:paddingEnd="14dp"
    android:paddingTop="12dp"
    android:paddingBottom="12dp">

    <TextView
        android:id="@+id/tvCheck"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="✓"
        android:textColor="@color/primary"
        android:textSize="15sp"
        android:textStyle="bold"
        android:layout_marginEnd="10dp"/>

    <TextView
        android:id="@+id/tvFeature"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:textColor="@color/text_primary"
        android:textSize="13sp"/>
</LinearLayout>
```

- [ ] **Step 3: bg_paywall_header.xml 생성**

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <gradient
        android:startColor="#1E3A8A"
        android:endColor="#1D4ED8"
        android:angle="135"/>
</shape>
```

- [ ] **Step 4: PaywallActivity.kt — feature row 텍스트 설정**

`PaywallActivity.kt`에서 `onCreate`에 아래 코드 추가:

```kotlin
listOf(
    binding.row1, binding.row2, binding.row3, binding.row4
).zip(listOf(
    "무제한 스트리밍",
    "AI 위험 감지 알림",
    "알림 이력 30일 보관",
    "오디오 스니펫 저장"
)).forEach { (row, text) ->
    row.findViewById<android.widget.TextView>(R.id.tvFeature).text = text
}
```

- [ ] **Step 5: 빌드 확인**

```bash
cd app && ./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add app/app/src/main/res/layout/activity_paywall.xml \
        app/app/src/main/res/layout/item_feature_row.xml \
        app/app/src/main/res/drawable/bg_paywall_header.xml \
        app/app/src/main/java/com/formykids/billing/PaywallActivity.kt
git commit -m "feat(ui): redesign PaywallActivity"
```

---

## Task 9: SettingsActivity 레이아웃

**Files:**
- Modify: `app/app/src/main/res/layout/activity_settings.xml`

- [ ] **Step 1: activity_settings.xml 교체**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="@color/background_blue">

    <!-- TopBar -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:background="@color/primary"
        android:paddingStart="20dp"
        android:paddingEnd="20dp"
        android:paddingTop="14dp"
        android:paddingBottom="14dp"
        android:orientation="horizontal"
        android:gravity="center_vertical">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="‹"
            android:textColor="#CCffffff"
            android:textSize="22sp"
            android:layout_marginEnd="12dp"
            android:clickable="true"
            android:focusable="true"
            android:onClick="onBackPressed"/>

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/settings"
            android:textColor="@color/white"
            android:textSize="16sp"
            android:textStyle="bold"/>
    </LinearLayout>

    <ScrollView
        android:layout_width="match_parent"
        android:layout_height="match_parent">
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:padding="20dp">

            <!-- 서버 섹션 -->
            <TextView
                style="@style/Widget.ForMyKids.SectionLabel"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="서버"
                android:layout_marginBottom="8dp"/>

            <com.google.android.material.card.MaterialCardView
                style="@style/Widget.ForMyKids.Card"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginBottom="20dp">
                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical"
                    android:padding="14dp">
                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="서버 주소"
                        android:textColor="@color/text_secondary"
                        android:textSize="12sp"
                        android:layout_marginBottom="6dp"/>
                    <com.google.android.material.textfield.TextInputLayout
                        android:id="@+id/tilServerUrl"
                        style="@style/Widget.ForMyKids.TextInput"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginBottom="10dp">
                        <com.google.android.material.textfield.TextInputEditText
                            android:id="@+id/etServerUrl"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:inputType="textUri"/>
                    </com.google.android.material.textfield.TextInputLayout>
                    <com.google.android.material.button.MaterialButton
                        android:id="@+id/btnSaveUrl"
                        style="@style/Widget.ForMyKids.Button.Primary"
                        android:layout_width="match_parent"
                        android:layout_height="48dp"
                        android:text="저장"/>
                </LinearLayout>
            </com.google.android.material.card.MaterialCardView>

            <!-- 구독 섹션 -->
            <TextView
                style="@style/Widget.ForMyKids.SectionLabel"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="구독"
                android:layout_marginBottom="8dp"/>

            <com.google.android.material.card.MaterialCardView
                android:id="@+id/btnManageSubscription"
                style="@style/Widget.ForMyKids.Card"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginBottom="20dp"
                android:clickable="true"
                android:focusable="true">
                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="horizontal"
                    android:padding="14dp"
                    android:gravity="center_vertical">
                    <LinearLayout
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_weight="1"
                        android:orientation="vertical">
                        <TextView
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="구독 관리"
                            android:textColor="@color/text_primary"
                            android:textSize="13sp"
                            android:textStyle="bold"/>
                        <TextView
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="Play Store에서 관리"
                            android:textColor="@color/text_hint"
                            android:textSize="11sp"
                            android:layout_marginTop="2dp"/>
                    </LinearLayout>
                    <ImageView
                        android:layout_width="16dp"
                        android:layout_height="16dp"
                        android:src="@drawable/ic_chevron_right"/>
                </LinearLayout>
            </com.google.android.material.card.MaterialCardView>

            <!-- 계정 섹션 -->
            <TextView
                style="@style/Widget.ForMyKids.SectionLabel"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="계정"
                android:layout_marginBottom="8dp"/>

            <com.google.android.material.card.MaterialCardView
                android:id="@+id/btnLogout"
                style="@style/Widget.ForMyKids.Card"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:clickable="true"
                android:focusable="true">
                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="horizontal"
                    android:padding="14dp"
                    android:gravity="center_vertical">
                    <TextView
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_weight="1"
                        android:text="로그아웃"
                        android:textColor="@color/danger"
                        android:textSize="13sp"
                        android:textStyle="bold"/>
                    <ImageView
                        android:layout_width="16dp"
                        android:layout_height="16dp"
                        android:src="@drawable/ic_chevron_right"
                        android:tint="@color/danger"/>
                </LinearLayout>
            </com.google.android.material.card.MaterialCardView>

        </LinearLayout>
    </ScrollView>
</LinearLayout>
```

- [ ] **Step 2: SettingsActivity.kt — ID 타입 수정**

`btnManageSubscription`, `btnLogout`이 `MaterialCardView`로 바뀌었으므로 `setOnClickListener` 호출은 그대로 동작한다 (ViewBinding). 변경 불필요.

- [ ] **Step 3: 빌드 확인**

```bash
cd app && ./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/app/src/main/res/layout/activity_settings.xml
git commit -m "feat(ui): redesign SettingsActivity with section cards"
```

---

## Task 10: AlertHistoryFragment — RecyclerView 교체

**Files:**
- Modify: `app/app/src/main/res/layout/fragment_alert_history.xml`
- Create: `app/app/src/main/res/layout/item_alert.xml`
- Create: `app/app/src/main/java/com/formykids/parent/AlertAdapter.kt`
- Modify: `app/app/src/main/java/com/formykids/parent/AlertHistoryFragment.kt`

- [ ] **Step 1: fragment_alert_history.xml 교체**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="@color/background_blue"
    android:padding="16dp">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/alert_history"
        android:textColor="@color/text_primary"
        android:textSize="18sp"
        android:textStyle="bold"
        android:layout_marginBottom="12dp"/>

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/rvAlerts"
        android:layout_width="match_parent"
        android:layout_height="match_parent"/>
</LinearLayout>
```

- [ ] **Step 2: item_alert.xml 생성**

```xml
<?xml version="1.0" encoding="utf-8"?>
<com.google.android.material.card.MaterialCardView
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    style="@style/Widget.ForMyKids.Card"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginBottom="8dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:padding="14dp">

        <TextView
            android:id="@+id/tvAlertType"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textSize="12sp"
            android:textStyle="bold"
            android:textColor="@color/white"
            android:background="@drawable/bg_alert_chip"
            android:paddingStart="8dp"
            android:paddingEnd="8dp"
            android:paddingTop="3dp"
            android:paddingBottom="3dp"
            android:layout_marginEnd="12dp"/>

        <LinearLayout
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:orientation="vertical">

            <TextView
                android:id="@+id/tvAlertTime"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:textColor="@color/text_primary"
                android:textSize="13sp"
                android:textStyle="bold"/>

            <TextView
                android:id="@+id/tvAlertConfidence"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:textColor="@color/text_hint"
                android:textSize="11sp"
                android:layout_marginTop="2dp"/>
        </LinearLayout>
    </LinearLayout>
</com.google.android.material.card.MaterialCardView>
```

- [ ] **Step 3: bg_alert_chip.xml 생성**

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#EF4444"/>
    <corners android:radius="20dp"/>
</shape>
```

- [ ] **Step 4: AlertAdapter.kt 생성**

```kotlin
package com.formykids.parent

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.formykids.R
import com.formykids.databinding.ItemAlertBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlertAdapter(private val items: List<Map<String, Any>>) :
    RecyclerView.Adapter<AlertAdapter.ViewHolder>() {

    private val fmt = SimpleDateFormat("MM/dd HH:mm", Locale.KOREA)

    inner class ViewHolder(val binding: ItemAlertBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAlertBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val alert = items[position]
        val ts = (alert["timestamp"] as? Long) ?: 0L
        val type = when (alert["type"]) {
            "scream" -> "비명"
            "cry" -> "울음"
            else -> "큰소리"
        }
        val conf = ((alert["confidence"] as? Double)?.times(100))?.toInt() ?: 0

        holder.binding.tvAlertTime.text = fmt.format(Date(ts))
        holder.binding.tvAlertType.text = type
        holder.binding.tvAlertConfidence.text = "신뢰도 ${conf}%"

        val chipColor = when (alert["type"]) {
            "scream" -> 0xFFEF4444.toInt()
            "cry" -> 0xFFF59E0B.toInt()
            else -> 0xFF2563EB.toInt()
        }
        (holder.binding.tvAlertType.background as? android.graphics.drawable.GradientDrawable)
            ?.setColor(chipColor)
    }

    override fun getItemCount() = items.size
}
```

- [ ] **Step 5: AlertHistoryFragment.kt 수정**

기존 `ListView` → `RecyclerView` 교체:

```kotlin
package com.formykids.parent

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.formykids.FirestoreManager
import com.formykids.databinding.FragmentAlertHistoryBinding
import kotlinx.coroutines.launch

class AlertHistoryFragment : Fragment() {
    private var _binding: FragmentAlertHistoryBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAlertHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.rvAlerts.layoutManager = LinearLayoutManager(requireContext())

        lifecycleScope.launch {
            if (!FirestoreManager.isPremium()) {
                binding.rvAlerts.adapter = AlertAdapter(
                    listOf(mapOf("timestamp" to 0L, "type" to "info", "confidence" to 0.0,
                        "_message" to "프리미엄 플랜에서 알림 이력을 확인할 수 있습니다."))
                )
                return@launch
            }
            val user = FirestoreManager.getCurrentUser() ?: return@launch
            val familyId = user["familyId"] as? String ?: return@launch
            val alerts = FirestoreManager.getAlerts(familyId)
            binding.rvAlerts.adapter = AlertAdapter(alerts)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
```

- [ ] **Step 6: 빌드 확인**

```bash
cd app && ./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Commit**

```bash
git add app/app/src/main/res/layout/fragment_alert_history.xml \
        app/app/src/main/res/layout/item_alert.xml \
        app/app/src/main/res/drawable/bg_alert_chip.xml \
        app/app/src/main/java/com/formykids/parent/AlertAdapter.kt \
        app/app/src/main/java/com/formykids/parent/AlertHistoryFragment.kt
git commit -m "feat(ui): replace ListView with RecyclerView in AlertHistoryFragment"
```

---

## Task 11: 최종 빌드 및 설치 테스트

- [ ] **Step 1: 전체 클린 빌드**

```bash
cd app && ./gradlew clean assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: 기기 설치**

```bash
/c/Users/junyoung/AppData/Local/Android/Sdk/platform-tools/adb.exe install -r app/build/outputs/apk/debug/app-debug.apk
```
Expected: `Performing Streamed Install` → `Success`

- [ ] **Step 3: 각 화면 수동 확인**

기기에서 앱을 실행하고 아래 항목을 순서대로 확인:
1. 온보딩 화면 — 상단 블루 헤더 + 방패 아이콘 표시
2. 역할 선택 — 부모/아이 카드 + 일러스트 표시
3. 페어링 — 블루 탑바 + 코드 카드 표시
4. 부모 메인 — 방패 탑바, 귀 아이콘 버튼, 볼륨 바, 알림 이력 카드 표시
5. 아이 메인 — 블루 그라디언트 헤더 + 펄스 인디케이터 + 상태 카드 표시
6. 페이월 — 기능 목록 + ₩4,900 가격 표시
7. 설정 — 섹션 구분 카드 표시

- [ ] **Step 4: 최종 Commit**

```bash
git add -A
git commit -m "feat(ui): complete Safe & Trustworthy UI redesign for all screens"
```
