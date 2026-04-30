# for-my-kids 런칭 전 체크리스트

**작성일:** 2026-04-22  
**상태:** 코드 구현 완료, 아래 항목들은 수동 작업 필요

---

## 1. Firebase 설정

- [ ] **Firebase Console에서 프로젝트 생성**
  - 프로젝트명 예: `for-my-kids`
  - 패키지명: `com.formykids`

- [ ] **google-services.json 다운로드 및 배치**
  - Firebase Console → 프로젝트 설정 → 앱 추가 (Android)
  - 패키지명 `com.formykids` 입력
  - 다운로드한 파일을 `app/app/google-services.json`에 교체 (현재 placeholder)

- [ ] **SHA-1 / SHA-256 지문 등록**
  - `./gradlew signingReport` 실행 후 debug keystore 지문을 Firebase Console에 등록
  - Phone Auth는 SHA-1 필수

- [ ] **Firebase 전화번호 인증 활성화**
  - Firebase Console → Authentication → Sign-in method → 전화 활성화

- [ ] **Firestore 데이터베이스 생성**
  - Firebase Console → Firestore Database → 데이터베이스 만들기
  - 리전: `asia-northeast3` (서울)
  - 시작 모드: 잠금 (프로덕션)

- [ ] **Firestore 복합 인덱스 생성**
  - 컬렉션: `alerts`
  - 필드 1: `familyId` (오름차순)
  - 필드 2: `timestamp` (내림차순)
  - Firebase Console → Firestore → 인덱스 → 복합 → 인덱스 추가

- [ ] **Firestore 보안 규칙 배포**

  ```
  rules_version = '2';
  service cloud.firestore {
    match /databases/{database}/documents {
      match /users/{uid} {
        allow read, write: if request.auth.uid == uid;
      }
      match /families/{familyId} {
        allow read: if request.auth != null;
        allow create: if request.auth != null &&
          request.resource.data.parentUids.hasAll([request.auth.uid]);
        allow update: if request.auth != null &&
          resource.data.parentUids.hasAll([request.auth.uid]) ||
          request.auth.uid == resource.data.childUid;
      }
      match /subscriptions/{uid} {
        allow read: if request.auth.uid == uid;
        allow write: if false; // 서버(Cloud Run)만 기록
      }
      match /alerts/{alertId} {
        allow read: if request.auth != null &&
          request.auth.uid in get(/databases/$(database)/documents/families/$(resource.data.familyId)).data.parentUids;
        allow write: if false; // 서버(Cloud Run)만 기록
      }
    }
  }
  ```

- [ ] **FCM 활성화 확인**
  - Firebase Console → Cloud Messaging → 활성화 상태 확인

---

## 2. YAMNet 모델 파일

- [ ] **yamnet.tflite 다운로드**
  - URL: https://tfhub.dev/google/lite-model/yamnet/classification/tflite/1
  - 다운로드 후 `app/app/src/main/assets/yamnet.tflite`에 교체 (현재 placeholder)

- [ ] **yamnet_labels.txt 다운로드**
  - URL: https://raw.githubusercontent.com/tensorflow/models/master/research/audioset/yamnet/yamnet_class_map.csv
  - 521개 클래스명 텍스트 파일 (`app/app/src/main/assets/yamnet_labels.txt`에 교체)
  - 한 줄에 하나씩 클래스명만 추출 (인덱스 0~520)

---

## 3. Cloud Run 서버 배포

- [ ] **GCP 프로젝트 생성 및 설정**
  ```bash
  gcloud projects create for-my-kids-prod
  gcloud config set project for-my-kids-prod
  gcloud services enable run.googleapis.com cloudbuild.googleapis.com
  ```

- [ ] **Firebase 서비스 계정 JSON 생성**
  - Firebase Console → 프로젝트 설정 → 서비스 계정 → 새 비공개 키 생성
  - JSON 파일 안전한 곳에 보관

- [ ] **Cloud Run 환경 변수 설정 및 배포**
  ```bash
  cd server
  gcloud run deploy family-monitor-relay \
    --source . \
    --region asia-northeast3 \
    --platform managed \
    --allow-unauthenticated \
    --port 8080 \
    --memory 512Mi \
    --min-instances 0 \
    --max-instances 10 \
    --set-env-vars "GOOGLE_PLAY_PACKAGE_NAME=com.formykids" \
    --set-env-vars "GOOGLE_PLAY_SUBSCRIPTION_ID=premium_monthly" \
    --set-secrets "FIREBASE_SERVICE_ACCOUNT_JSON=firebase-sa-json:latest"
  ```

- [ ] **Cloud Run URL 확인 후 앱에 적용**
  - 배포 후 표시되는 URL (예: `https://family-monitor-relay-xxxx-du.a.run.app`)
  - `app/app/src/main/java/com/formykids/App.kt` 의 `DEFAULT_SERVER_URL` 값을 `wss://YOUR_CLOUD_RUN_URL` → 실제 URL로 교체
  - 예: `const val DEFAULT_SERVER_URL = "wss://family-monitor-relay-xxxx-du.a.run.app"`

- [ ] **헬스체크 확인**
  ```bash
  curl https://YOUR_CLOUD_RUN_URL/health
  # 응답: {"ok":true}
  ```

---

## 4. Google Play 구독 상품 설정

- [ ] **Google Play Console에서 앱 등록**
  - Play Console → 앱 만들기 → 패키지명 `com.formykids`

- [ ] **인앱 상품 → 구독 생성**
  - 상품 ID: `premium_monthly` (코드에 하드코딩된 값, 변경 불가)
  - 가격: 4,900원/월 (시장 조사 후 확정)
  - 결제 기간: 1개월
  - 무료 체험: 선택사항 (7일 권장)

- [ ] **Google Play Developer API 서비스 계정 연결**
  - Play Console → 설정 → API 액세스 → 서비스 계정 연결
  - 서버의 `/verify-purchase` 엔드포인트가 이 계정으로 구독 검증함

---

## 5. 앱 빌드 및 배포

- [ ] **릴리즈 키스토어 생성**
  ```bash
  keytool -genkey -v -keystore for-my-kids-release.jks \
    -alias for-my-kids -keyalg RSA -keysize 2048 -validity 10000
  ```

- [ ] **`app/build.gradle`에 서명 설정 추가**
  ```groovy
  android {
    signingConfigs {
      release {
        storeFile file("for-my-kids-release.jks")
        storePassword "..."
        keyAlias "for-my-kids"
        keyPassword "..."
      }
    }
    buildTypes {
      release { signingConfig signingConfigs.release }
    }
  }
  ```

- [ ] **릴리즈 APK/AAB 빌드**
  ```bash
  ./gradlew bundleRelease
  # 결과물: app/build/outputs/bundle/release/app-release.aab
  ```

- [ ] **Play Console에 AAB 업로드** (내부 테스트 → 비공개 테스트 → 프로덕션 순서 권장)

---

## 6. 런칭 전 최종 확인

- [ ] 부모↔아이 기기 실제 페어링 테스트
- [ ] 오디오 스트리밍 지연 확인 (목표: 1초 이내)
- [ ] YAMNet 위험 감지 → FCM 푸시 수신 E2E 테스트
- [ ] 무료 30분 제한 → PaywallActivity 표시 확인
- [ ] 구독 결제 → 프리미엄 전환 확인
- [ ] Cloud Run 콜드 스타트 시 자동 재연결 확인 (앱에 `scheduleReconnect` 로직 존재)
- [ ] Firebase Spark 무료 한도 모니터링 설정 (읽기 5만/일 초과 시 Blaze 전환)
