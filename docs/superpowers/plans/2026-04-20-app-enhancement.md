# for-my-kids App Enhancement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Transform the POC audio-streaming app into a production-ready family monitoring app with Firebase Auth, AI danger detection (YAMNet), FCM push alerts, and Google Play Billing subscription.

**Architecture:** Child device records at 16kHz, streams PCM via WebSocket relay on Cloud Run, and runs on-device YAMNet inference to detect danger sounds (crying/screaming); when triggered, server sends FCM push to parent. Firebase handles auth, Firestore for user/subscription data, Google Play Billing for subscriptions.

**Tech Stack:** Kotlin Android (TF Lite, Firebase Auth/Firestore/FCM, Play Billing v7, OkHttp3, Coroutines), Node.js server (Express, ws, firebase-admin, googleapis), GCP Cloud Run, Firebase project.

---

## File Map

### Server (`server/`)
| File | Status | Responsibility |
|------|--------|----------------|
| `server.js` | Modify | Express + ws entry point, route mounting |
| `sessions.js` | Create | familyId-based multi-session state |
| `auth.js` | Create | Firebase ID token verification |
| `fcm.js` | Create | FCM push notification sender |
| `billing.js` | Create | Google Play subscription verification |
| `package.json` | Modify | Add express, firebase-admin, googleapis, jest |
| `Dockerfile` | Modify | Port 8080, production-ready |
| `.env.example` | Create | Required env vars documentation |
| `__tests__/sessions.test.js` | Create | Session management unit tests |
| `__tests__/billing.test.js` | Create | Billing verification unit tests |

### Android (`app/app/src/main/java/com/formykids/`)
| File | Status | Responsibility |
|------|--------|----------------|
| `App.kt` | Modify | Remove hardcoded SERVER_URL, add FCM channel |
| `WebSocketManager.kt` | Modify | Add auth token + familyId to register message |
| `FirestoreManager.kt` | Create | Firestore CRUD (user, subscription, alerts) |
| `auth/OnboardingActivity.kt` | Create | Firebase phone number auth screen |
| `auth/PairingActivity.kt` | Create | 6-digit code generation (parent) / entry (child) |
| `child/DangerDetector.kt` | Create | YAMNet TF Lite inference wrapper |
| `child/AudioStreamService.kt` | Modify | 16kHz recording, integrate DangerDetector |
| `child/ChildActivity.kt` | Modify | Show AI detection status |
| `parent/ParentActivity.kt` | Modify | FCM init, subscription gate, alert history tab |
| `parent/AlertHistoryFragment.kt` | Create | List of past danger alerts from Firestore |
| `settings/SettingsActivity.kt` | Create | Server URL, sensitivity, logout |
| `billing/BillingManager.kt` | Create | Play Billing v7 wrapper |
| `billing/PaywallActivity.kt` | Create | Subscription offer screen |
| `SplashActivity.kt` | Create | Auth state check, routing |

### Android Tests (`app/app/src/test/java/com/formykids/`)
| File | Status |
|------|--------|
| `child/DangerDetectorTest.kt` | Create |
| `billing/BillingManagerTest.kt` | Create |
| `parent/VolumeAnalyzerTest.kt` | Exists — extend |

---

## Phase 1: Server Infrastructure

### Task 1: Add server dependencies and test setup

**Files:**
- Modify: `server/package.json`
- Create: `server/.env.example`

- [ ] **Step 1: Update package.json**

```json
{
  "name": "family-monitor-server",
  "version": "2.0.0",
  "main": "server.js",
  "scripts": {
    "start": "node server.js",
    "test": "jest --testEnvironment node"
  },
  "dependencies": {
    "express": "^4.19.2",
    "ws": "^8.17.0",
    "firebase-admin": "^12.1.0",
    "googleapis": "^140.0.0"
  },
  "devDependencies": {
    "jest": "^29.7.0"
  }
}
```

- [ ] **Step 2: Create .env.example**

```
PORT=8080
FIREBASE_SERVICE_ACCOUNT_JSON={"type":"service_account",...}
GOOGLE_PLAY_PACKAGE_NAME=com.formykids
GOOGLE_PLAY_SUBSCRIPTION_ID=premium_monthly
```

- [ ] **Step 3: Install dependencies**

```bash
cd server && npm install
```

Expected: `node_modules/` populated, no errors.

- [ ] **Step 4: Commit**

```bash
git add server/package.json server/.env.example server/package-lock.json
git commit -m "feat(server): add express, firebase-admin, googleapis, jest"
```

---

### Task 2: Implement session management

**Files:**
- Create: `server/sessions.js`
- Create: `server/__tests__/sessions.test.js`

- [ ] **Step 1: Write the failing tests**

```javascript
// server/__tests__/sessions.test.js
const { getOrCreate, removeClient, sessions } = require('../sessions');

beforeEach(() => sessions.clear());

test('getOrCreate creates session for new familyId', () => {
  const s = getOrCreate('fam1');
  expect(s.child).toBeNull();
  expect(s.parents.size).toBe(0);
  expect(s.listeningParents.size).toBe(0);
});

test('getOrCreate returns same session for same familyId', () => {
  const s1 = getOrCreate('fam1');
  const s2 = getOrCreate('fam1');
  expect(s1).toBe(s2);
});

test('removeClient finds child by ws reference', () => {
  const ws = { id: 'child-ws' };
  const s = getOrCreate('fam1');
  s.child = ws;
  const result = removeClient(ws);
  expect(result).toEqual({ familyId: 'fam1', role: 'child' });
  expect(s.child).toBeNull();
});

test('removeClient finds parent and reports wasListening', () => {
  const ws = { id: 'parent-ws' };
  const s = getOrCreate('fam1');
  s.parents.add(ws);
  s.listeningParents.add(ws);
  const result = removeClient(ws);
  expect(result.role).toBe('parent');
  expect(result.wasListening).toBe(true);
  expect(s.parents.has(ws)).toBe(false);
});

test('removeClient returns null for unknown ws', () => {
  expect(removeClient({ id: 'unknown' })).toBeNull();
});
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
cd server && npm test -- --testPathPattern=sessions
```

Expected: FAIL (sessions.js not found)

- [ ] **Step 3: Implement sessions.js**

```javascript
// server/sessions.js
const sessions = new Map();

function getOrCreate(familyId) {
  if (!sessions.has(familyId)) {
    sessions.set(familyId, {
      child: null,
      parents: new Set(),
      listeningParents: new Set(),
      dailyStreamedSeconds: 0,
      dailyResetAt: Date.now(),
    });
  }
  return sessions.get(familyId);
}

function removeClient(ws) {
  for (const [familyId, session] of sessions) {
    if (session.child === ws) {
      session.child = null;
      session.listeningParents.clear();
      return { familyId, role: 'child' };
    }
    if (session.parents.has(ws)) {
      session.parents.delete(ws);
      const wasListening = session.listeningParents.delete(ws);
      return { familyId, role: 'parent', wasListening };
    }
  }
  return null;
}

module.exports = { getOrCreate, removeClient, sessions };
```

- [ ] **Step 4: Run tests to confirm they pass**

```bash
cd server && npm test -- --testPathPattern=sessions
```

Expected: PASS (5 tests)

- [ ] **Step 5: Commit**

```bash
git add server/sessions.js server/__tests__/sessions.test.js
git commit -m "feat(server): add familyId-based session management"
```

---

### Task 3: Implement Firebase Auth + FCM modules

**Files:**
- Create: `server/auth.js`
- Create: `server/fcm.js`

- [ ] **Step 1: Create auth.js**

```javascript
// server/auth.js
const admin = require('firebase-admin');

let initialized = false;

function initFirebase() {
  if (initialized) return;
  const serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT_JSON);
  admin.initializeApp({ credential: admin.credential.cert(serviceAccount) });
  initialized = true;
}

async function verifyIdToken(idToken) {
  initFirebase();
  const decoded = await admin.auth().verifyIdToken(idToken);
  return decoded.uid;
}

function getFirestore() {
  initFirebase();
  return admin.firestore();
}

function getMessaging() {
  initFirebase();
  return admin.messaging();
}

module.exports = { verifyIdToken, getFirestore, getMessaging, initFirebase };
```

- [ ] **Step 2: Create fcm.js**

```javascript
// server/fcm.js
const { getMessaging, getFirestore } = require('./auth');

const TYPE_LABELS = { scream: '비명', cry: '울음', loud: '큰 소리' };

async function sendDangerAlert({ fcmToken, type, confidence, familyId, timestamp }) {
  await getMessaging().send({
    token: fcmToken,
    notification: {
      title: '⚠️ 위험 감지',
      body: `${TYPE_LABELS[type] ?? type} 소리가 감지되었습니다`,
    },
    data: { type, confidence: String(confidence), familyId },
    android: { priority: 'high', notification: { channelId: 'danger_alerts' } },
  });

  await getFirestore().collection('alerts').add({
    familyId,
    timestamp: timestamp ?? Date.now(),
    type,
    confidence,
  });
}

module.exports = { sendDangerAlert };
```

- [ ] **Step 3: Commit**

```bash
git add server/auth.js server/fcm.js
git commit -m "feat(server): add Firebase auth verification and FCM sender"
```

---

### Task 4: Implement Play Billing verification

**Files:**
- Create: `server/billing.js`
- Create: `server/__tests__/billing.test.js`

- [ ] **Step 1: Write failing test**

```javascript
// server/__tests__/billing.test.js
jest.mock('googleapis', () => ({
  google: {
    auth: { GoogleAuth: jest.fn().mockImplementation(() => ({})) },
    androidpublisher: jest.fn().mockReturnValue({
      purchases: {
        subscriptions: {
          get: jest.fn().mockResolvedValue({
            data: { paymentState: 1, expiryTimeMillis: '9999999999999' }
          })
        }
      }
    })
  }
}));

const { verifySubscription } = require('../billing');

test('verifySubscription returns subscription data', async () => {
  const result = await verifySubscription('com.formykids', 'premium_monthly', 'tok123');
  expect(result.paymentState).toBe(1);
  expect(result.expiryTimeMillis).toBe('9999999999999');
});
```

- [ ] **Step 2: Run test to confirm it fails**

```bash
cd server && npm test -- --testPathPattern=billing
```

Expected: FAIL (billing.js not found)

- [ ] **Step 3: Implement billing.js**

```javascript
// server/billing.js
const { google } = require('googleapis');

async function verifySubscription(packageName, subscriptionId, purchaseToken) {
  const auth = new google.auth.GoogleAuth({
    scopes: ['https://www.googleapis.com/auth/androidpublisher'],
  });
  const publisher = google.androidpublisher({ version: 'v3', auth });
  const res = await publisher.purchases.subscriptions.get({
    packageName,
    subscriptionId,
    token: purchaseToken,
  });
  return res.data;
}

module.exports = { verifySubscription };
```

- [ ] **Step 4: Run test to confirm it passes**

```bash
cd server && npm test -- --testPathPattern=billing
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add server/billing.js server/__tests__/billing.test.js
git commit -m "feat(server): add Google Play subscription verification"
```

---

### Task 5: Rewrite server.js with Express + familyId sessions

**Files:**
- Modify: `server/server.js`

- [ ] **Step 1: Replace server.js**

```javascript
// server/server.js
require('dotenv').config();
const express = require('express');
const http = require('http');
const WebSocket = require('ws');
const { getOrCreate, removeClient } = require('./sessions');
const { verifyIdToken, getFirestore } = require('./auth');
const { sendDangerAlert } = require('./fcm');
const { verifySubscription } = require('./billing');

const app = express();
app.use(express.json());

const PACKAGE_NAME = process.env.GOOGLE_PLAY_PACKAGE_NAME;
const SUBSCRIPTION_ID = process.env.GOOGLE_PLAY_SUBSCRIPTION_ID;
const FREE_DAILY_LIMIT_SECONDS = 1800;

app.get('/health', (_, res) => res.json({ ok: true }));

app.post('/verify-purchase', async (req, res) => {
  const { idToken, purchaseToken } = req.body;
  if (!idToken || !purchaseToken) return res.status(400).json({ error: 'missing fields' });
  try {
    const uid = await verifyIdToken(idToken);
    const data = await verifySubscription(PACKAGE_NAME, SUBSCRIPTION_ID, purchaseToken);
    const expiresAt = parseInt(data.expiryTimeMillis);
    await getFirestore().collection('subscriptions').doc(uid).set(
      { plan: 'premium', expiresAt, purchaseToken },
      { merge: true }
    );
    res.json({ ok: true, expiresAt });
  } catch (e) {
    res.status(400).json({ error: e.message });
  }
});

const server = http.createServer(app);
const wss = new WebSocket.Server({ server });

// wsClients: Map<ws, { uid, familyId, role }>
const wsClients = new Map();

function safeSend(ws, data) {
  if (ws && ws.readyState === WebSocket.OPEN) ws.send(data);
}

wss.on('connection', (ws) => {
  ws.on('message', async (data, isBinary) => {
    if (isBinary) {
      const meta = wsClients.get(ws);
      if (!meta || meta.role !== 'child') return;
      const session = getOrCreate(meta.familyId);

      // free tier streaming limit check
      const subDoc = await getFirestore().collection('subscriptions').doc(meta.uid).get();
      const sub = subDoc.data() ?? {};
      if (sub.plan !== 'premium') {
        const now = Date.now();
        if (now - (sub.dailyResetAt ?? 0) > 86400000) {
          await getFirestore().collection('subscriptions').doc(meta.uid).set(
            { dailyStreamedSeconds: 0, dailyResetAt: now }, { merge: true }
          );
          sub.dailyStreamedSeconds = 0;
        }
        if ((sub.dailyStreamedSeconds ?? 0) >= FREE_DAILY_LIMIT_SECONDS) {
          safeSend(ws, JSON.stringify({ type: 'stream_limit_reached' }));
          return;
        }
        // increment by chunk size (1600 samples / 16000 Hz = 0.1 sec)
        await getFirestore().collection('subscriptions').doc(meta.uid).set(
          { dailyStreamedSeconds: (sub.dailyStreamedSeconds ?? 0) + 0.1 }, { merge: true }
        );
      }
      session.listeningParents.forEach(p => safeSend(p, data));
      return;
    }

    let msg;
    try { msg = JSON.parse(data.toString()); } catch { return; }

    if (msg.type === 'auth') {
      try {
        const uid = await verifyIdToken(msg.idToken);
        const userDoc = await getFirestore().collection('users').doc(uid).get();
        const user = userDoc.data();
        if (!user) return safeSend(ws, JSON.stringify({ type: 'auth_error', reason: 'user not found' }));
        wsClients.set(ws, { uid, familyId: user.familyId, role: user.role });
        const session = getOrCreate(user.familyId);
        if (user.role === 'child') {
          if (session.child && session.child !== ws) session.child.close(1000, 'replaced');
          session.child = ws;
        } else {
          session.parents.add(ws);
          safeSend(ws, JSON.stringify({ type: 'status', listeningCount: session.listeningParents.size }));
        }
        safeSend(ws, JSON.stringify({ type: 'auth_ok', familyId: user.familyId }));
      } catch (e) {
        safeSend(ws, JSON.stringify({ type: 'auth_error', reason: e.message }));
      }
      return;
    }

    const meta = wsClients.get(ws);
    if (!meta) return;
    const session = getOrCreate(meta.familyId);

    if (msg.type === 'start_listen' && meta.role === 'parent') {
      const wasZero = session.listeningParents.size === 0;
      session.listeningParents.add(ws);
      if (wasZero) safeSend(session.child, JSON.stringify({ type: 'start_stream' }));
      session.parents.forEach(p => safeSend(p, JSON.stringify({ type: 'status', listeningCount: session.listeningParents.size })));
    } else if (msg.type === 'stop_listen' && meta.role === 'parent') {
      session.listeningParents.delete(ws);
      if (session.listeningParents.size === 0) safeSend(session.child, JSON.stringify({ type: 'stop_stream' }));
      session.parents.forEach(p => safeSend(p, JSON.stringify({ type: 'status', listeningCount: session.listeningParents.size })));
    } else if (msg.type === 'danger_alert' && meta.role === 'child') {
      const parentTokens = await getParentFcmTokens(meta.familyId);
      await Promise.all(parentTokens.map(token =>
        sendDangerAlert({ fcmToken: token, type: msg.class, confidence: msg.confidence, familyId: meta.familyId })
      ));
    }
  });

  ws.on('close', () => {
    const result = removeClient(ws);
    wsClients.delete(ws);
    if (!result) return;
    const session = getOrCreate(result.familyId);
    if (result.role === 'child') {
      safeSend(session.child, null); // child gone, no-op
    } else if (result.wasListening && session.listeningParents.size === 0) {
      safeSend(session.child, JSON.stringify({ type: 'stop_stream' }));
    }
  });

  ws.on('error', err => console.error('Socket error:', err.message));
});

async function getParentFcmTokens(familyId) {
  const familyDoc = await getFirestore().collection('families').doc(familyId).get();
  const family = familyDoc.data();
  if (!family?.parentUids?.length) return [];
  const userDocs = await Promise.all(
    family.parentUids.map(uid => getFirestore().collection('users').doc(uid).get())
  );
  return userDocs.map(d => d.data()?.fcmToken).filter(Boolean);
}

const PORT = process.env.PORT ?? 8080;
server.listen(PORT, () => console.log(`Server listening on port ${PORT}`));
```

- [ ] **Step 2: Run all server tests to confirm nothing broken**

```bash
cd server && npm test
```

Expected: All pass (sessions + billing tests)

- [ ] **Step 3: Commit**

```bash
git add server/server.js
git commit -m "feat(server): rewrite with Express, familyId sessions, danger_alert handling"
```

---

### Task 6: Update Dockerfile and deploy to Cloud Run

**Files:**
- Modify: `server/Dockerfile`
- Modify: `server/docker-compose.yml`

- [ ] **Step 1: Update Dockerfile**

```dockerfile
FROM node:20-alpine
WORKDIR /app
COPY package*.json ./
RUN npm ci --omit=dev
COPY . .
EXPOSE 8080
CMD ["node", "server.js"]
```

- [ ] **Step 2: Update docker-compose.yml for local dev**

```yaml
version: '3.8'
services:
  server:
    build: .
    ports:
      - "8080:8080"
    env_file:
      - .env
```

- [ ] **Step 3: Test Docker build locally**

```bash
cd server && docker build -t family-monitor-server .
```

Expected: Build succeeds.

- [ ] **Step 4: Deploy to Cloud Run**

Prerequisites: `gcloud` CLI authenticated, Firebase project created, service account JSON ready.

```bash
# Set your project
gcloud config set project YOUR_GCP_PROJECT_ID

# Enable required APIs
gcloud services enable run.googleapis.com cloudbuild.googleapis.com

# Deploy
gcloud run deploy family-monitor-relay \
  --source server/ \
  --region asia-northeast3 \
  --allow-unauthenticated \
  --min-instances 0 \
  --max-instances 10 \
  --memory 512Mi \
  --set-env-vars "FIREBASE_SERVICE_ACCOUNT_JSON=$(cat path/to/serviceAccount.json | tr -d '\n'),GOOGLE_PLAY_PACKAGE_NAME=com.formykids,GOOGLE_PLAY_SUBSCRIPTION_ID=premium_monthly"
```

Expected: Cloud Run URL printed. Test with:

```bash
curl https://YOUR_CLOUD_RUN_URL/health
# → {"ok":true}
```

- [ ] **Step 5: Commit**

```bash
git add server/Dockerfile server/docker-compose.yml
git commit -m "feat(server): update Dockerfile for Cloud Run port 8080"
```

---

## Phase 2: Firebase Auth + Device Pairing

### Task 7: Add Firebase + Billing dependencies to Android

**Files:**
- Modify: `app/app/build.gradle.kts`
- Modify: `app/build.gradle.kts` (root-level if needed)

- [ ] **Step 1: Add Firebase and Billing to app/app/build.gradle.kts**

Add inside `dependencies { }`:

```kotlin
// Firebase
implementation(platform("com.google.firebase:firebase-bom:33.1.0"))
implementation("com.google.firebase:firebase-auth-ktx")
implementation("com.google.firebase:firebase-firestore-ktx")
implementation("com.google.firebase:firebase-messaging-ktx")

// TF Lite for YAMNet
implementation("org.tensorflow:tensorflow-lite:2.16.1")
implementation("org.tensorflow:tensorflow-lite-support:0.4.4")

// Play Billing
implementation("com.android.billingclient:billing-ktx:7.0.0")
```

Add `google-services` plugin at top of `app/app/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}
```

Add to root `app/build.gradle.kts` plugins block:

```kotlin
id("com.google.gms.google-services") version "4.4.2" apply false
```

- [ ] **Step 2: Add google-services.json**

Download `google-services.json` from Firebase Console (Project Settings → Your apps → Android app `com.formykids`) and place at `app/app/google-services.json`.

- [ ] **Step 3: Download YAMNet model**

Download from: https://tfhub.dev/google/lite-model/yamnet/classification/tflite/1

Place at `app/app/src/main/assets/yamnet.tflite`

- [ ] **Step 4: Sync and build**

```bash
cd app && ./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/app/build.gradle.kts app/build.gradle.kts app/app/google-services.json app/app/src/main/assets/yamnet.tflite
git commit -m "feat(android): add Firebase, TF Lite, Play Billing dependencies"
```

---

### Task 8: Add FirestoreManager and update App.kt

**Files:**
- Create: `app/app/src/main/java/com/formykids/FirestoreManager.kt`
- Modify: `app/app/src/main/java/com/formykids/App.kt`
- Modify: `app/app/src/main/java/com/formykids/WebSocketManager.kt`

- [ ] **Step 1: Create FirestoreManager.kt**

```kotlin
package com.formykids

import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
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
}
```

- [ ] **Step 2: Update App.kt — remove hardcoded URL, add danger alert channel**

```kotlin
package com.formykids

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel(NOTIFICATION_CHANNEL_ID, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW)
        createNotificationChannel(DANGER_CHANNEL_ID, "위험 감지 알림", NotificationManager.IMPORTANCE_HIGH)
    }

    private fun createNotificationChannel(id: String, name: String, importance: Int) {
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(id, name, importance))
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "for_my_kids"
        const val DANGER_CHANNEL_ID = "danger_alerts"
        const val PREF_NAME = "for_my_kids_prefs"
        const val PREF_SERVER_URL = "server_url"
        const val DEFAULT_SERVER_URL = "wss://YOUR_CLOUD_RUN_URL"
        const val ROLE_PARENT = "parent"
        const val ROLE_CHILD = "child"
    }
}
```

Replace `DEFAULT_SERVER_URL` value with actual Cloud Run URL from Task 6 Step 4.

- [ ] **Step 3: Update WebSocketManager.kt — add auth token to register**

Replace `connect()` and add `connectWithAuth()`:

```kotlin
fun connectWithAuth(idToken: String, onAuthOk: (familyId: String) -> Unit) {
    this.idToken = idToken
    this.onAuthOk = onAuthOk
    shouldReconnect = true
    openSocket()
}

// In onOpen callback, replace the old register send with:
override fun onOpen(ws: WebSocket, response: Response) {
    reconnectJob?.cancel()
    if (idToken != null) {
        ws.send("""{"type":"auth","idToken":"$idToken"}""")
    }
    onConnected?.invoke()
}

// In onTextMessage, handle auth_ok:
override fun onMessage(ws: WebSocket, text: String) {
    val msg = runCatching { org.json.JSONObject(text) }.getOrNull()
    if (msg?.getString("type") == "auth_ok") {
        onAuthOk?.invoke(msg.getString("familyId"))
    }
    onTextMessage?.invoke(text)
}
```

Add private fields to `WebSocketManager`:

```kotlin
private var idToken: String? = null
private var onAuthOk: ((String) -> Unit)? = null
```

- [ ] **Step 3b: Update AudioStreamService and ParentActivity to use connectWithAuth()**

In `AudioStreamService.setupWebSocket()`, replace the `WebSocketManager.connect()` call and the `onConnected` lambda's register send:

```kotlin
private fun setupWebSocket() {
    scope.launch {
        val idToken = com.google.firebase.auth.ktx.auth.currentUser
            ?.getIdToken(false)?.await()?.token ?: return@launch
        WebSocketManager.connectWithAuth(idToken) { familyId ->
            connectionCallback?.invoke(true)
        }
        WebSocketManager.onDisconnected = { connectionCallback?.invoke(false) }
        WebSocketManager.onTextMessage = { text ->
            val msg = org.json.JSONObject(text)
            when (msg.getString("type")) {
                "start_stream" -> startStreaming()
                "stop_stream" -> stopStreaming()
                "stream_limit_reached" -> { stopStreaming(); limitCallback?.invoke() }
            }
        }
    }
}
```

In `ParentActivity.setupWebSocket()`, replace `WebSocketManager.connect()`:

```kotlin
lifecycleScope.launch {
    val idToken = com.google.firebase.auth.ktx.auth.currentUser
        ?.getIdToken(false)?.await()?.token ?: return@launch
    WebSocketManager.connectWithAuth(idToken) { _ -> /* familyId available if needed */ }
}
```

- [ ] **Step 4: Build to check for compile errors**

```bash
cd app && ./gradlew compileDebugKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/app/src/main/java/com/formykids/
git commit -m "feat(android): add FirestoreManager, remove hardcoded server URL, add auth to WebSocket"
```

---

### Task 9: Build SplashActivity and OnboardingActivity

**Files:**
- Create: `app/app/src/main/java/com/formykids/SplashActivity.kt`
- Create: `app/app/src/main/java/com/formykids/auth/OnboardingActivity.kt`
- Modify: `app/app/src/main/AndroidManifest.xml` (set SplashActivity as launcher)

- [ ] **Step 1: Create SplashActivity.kt**

```kotlin
package com.formykids

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.formykids.auth.OnboardingActivity
import com.formykids.child.ChildActivity
import com.formykids.parent.ParentActivity
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val user = Firebase.auth.currentUser
        if (user == null) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }
        lifecycleScope.launch {
            val userData = FirestoreManager.getCurrentUser()
            val role = userData?.get("role") as? String
            val target = if (role == App.ROLE_PARENT) ParentActivity::class.java else ChildActivity::class.java
            startActivity(Intent(this@SplashActivity, target))
            finish()
        }
    }
}
```

- [ ] **Step 2: Create OnboardingActivity.kt**

```kotlin
package com.formykids.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.formykids.App
import com.formykids.R
import com.formykids.databinding.ActivityOnboardingBinding
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.util.concurrent.TimeUnit

class OnboardingActivity : AppCompatActivity() {
    private lateinit var binding: ActivityOnboardingBinding
    private var verificationId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnSendCode.setOnClickListener {
            val phone = binding.etPhone.text.toString().trim()
            sendVerificationCode("+82${phone.removePrefix("0")}")
        }

        binding.btnVerify.setOnClickListener {
            val code = binding.etCode.text.toString().trim()
            val vid = verificationId ?: return@setOnClickListener
            val credential = PhoneAuthProvider.getCredential(vid, code)
            signInWithCredential(credential)
        }
    }

    private fun sendVerificationCode(phoneNumber: String) {
        val options = PhoneAuthOptions.newBuilder(Firebase.auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    signInWithCredential(credential)
                }
                override fun onVerificationFailed(e: Exception) {
                    Toast.makeText(this@OnboardingActivity, "인증 실패: ${e.message}", Toast.LENGTH_LONG).show()
                }
                override fun onCodeSent(vid: String, token: PhoneAuthProvider.ForceResendingToken) {
                    verificationId = vid
                    binding.layoutCodeEntry.visibility = android.view.View.VISIBLE
                }
            })
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    private fun signInWithCredential(credential: PhoneAuthCredential) {
        Firebase.auth.signInWithCredential(credential).addOnSuccessListener {
            startActivity(Intent(this, RoleSelectActivity::class.java))
            finish()
        }.addOnFailureListener {
            Toast.makeText(this, "로그인 실패: ${it.message}", Toast.LENGTH_LONG).show()
        }
    }
}
```

Note: `RoleSelectActivity` and `PairingActivity` are created in the next task. `activity_onboarding.xml` layout needs: `etPhone` (EditText), `btnSendCode` (Button), `layoutCodeEntry` (View group), `etCode` (EditText), `btnVerify` (Button).

- [ ] **Step 3: Update AndroidManifest.xml launcher activity**

Change launcher activity from `MainActivity` to `SplashActivity`:

```xml
<activity
    android:name=".SplashActivity"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity>
<activity android:name=".auth.OnboardingActivity" android:exported="false" />
<activity android:name=".auth.RoleSelectActivity" android:exported="false" />
<activity android:name=".auth.PairingActivity" android:exported="false" />
```

- [ ] **Step 4: Build**

```bash
cd app && ./gradlew compileDebugKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/app/src/main/java/com/formykids/ app/app/src/main/AndroidManifest.xml
git commit -m "feat(android): add SplashActivity, OnboardingActivity with Firebase phone auth"
```

---

### Task 10: Build RoleSelectActivity and PairingActivity

**Files:**
- Create: `app/app/src/main/java/com/formykids/auth/RoleSelectActivity.kt`
- Create: `app/app/src/main/java/com/formykids/auth/PairingActivity.kt`

- [ ] **Step 1: Create RoleSelectActivity.kt**

```kotlin
package com.formykids.auth

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.formykids.App
import com.formykids.databinding.ActivityRoleSelectBinding

class RoleSelectActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRoleSelectBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRoleSelectBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnParent.setOnClickListener { launch(App.ROLE_PARENT) }
        binding.btnChild.setOnClickListener { launch(App.ROLE_CHILD) }
    }

    private fun launch(role: String) {
        startActivity(Intent(this, PairingActivity::class.java).putExtra("role", role))
        finish()
    }
}
```

- [ ] **Step 2: Create PairingActivity.kt**

```kotlin
package com.formykids.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.formykids.App
import com.formykids.child.ChildActivity
import com.formykids.databinding.ActivityPairingBinding
import com.formykids.parent.ParentActivity
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

class PairingActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPairingBinding
    private val role by lazy { intent.getStringExtra("role") ?: App.ROLE_CHILD }
    private val db get() = Firebase.firestore
    private val uid get() = Firebase.auth.currentUser!!.uid

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPairingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (role == App.ROLE_PARENT) setupParentFlow() else setupChildFlow()
    }

    private fun setupParentFlow() {
        binding.layoutParent.visibility = android.view.View.VISIBLE
        val code = String.format("%06d", Random.nextInt(1000000))
        binding.tvPairingCode.text = code
        lifecycleScope.launch {
            val familyRef = db.collection("families").document()
            familyRef.set(mapOf(
                "parentUids" to listOf(uid),
                "childUid" to null,
                "pairingCode" to code,
                "pairingExpiresAt" to System.currentTimeMillis() + 600_000
            )).await()
            db.collection("users").document(uid).set(mapOf(
                "role" to App.ROLE_PARENT,
                "familyId" to familyRef.id,
                "fcmToken" to "",
                "createdAt" to System.currentTimeMillis()
            )).await()
        }
        binding.btnCodeDone.setOnClickListener {
            startActivity(Intent(this, ParentActivity::class.java))
            finish()
        }
    }

    private fun setupChildFlow() {
        binding.layoutChild.visibility = android.view.View.VISIBLE
        binding.btnSubmitCode.setOnClickListener {
            val code = binding.etCode.text.toString().trim()
            lifecycleScope.launch { submitCode(code) }
        }
    }

    private suspend fun submitCode(code: String) {
        val snap = db.collection("families")
            .whereEqualTo("pairingCode", code)
            .whereGreaterThan("pairingExpiresAt", System.currentTimeMillis())
            .get().await()
        if (snap.isEmpty) {
            runOnUiThread { Toast.makeText(this, "코드가 올바르지 않거나 만료되었습니다", Toast.LENGTH_SHORT).show() }
            return
        }
        val familyDoc = snap.documents.first()
        val familyId = familyDoc.id
        familyDoc.reference.update("childUid", uid).await()
        db.collection("users").document(uid).set(mapOf(
            "role" to App.ROLE_CHILD,
            "familyId" to familyId,
            "fcmToken" to "",
            "createdAt" to System.currentTimeMillis()
        )).await()
        startActivity(Intent(this, ChildActivity::class.java))
        finish()
    }
}
```

Note: `activity_pairing.xml` needs: `layoutParent` (hidden by default) with `tvPairingCode` and `btnCodeDone`; `layoutChild` (hidden by default) with `etCode` and `btnSubmitCode`.

- [ ] **Step 3: Build**

```bash
cd app && ./gradlew compileDebugKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/app/src/main/java/com/formykids/auth/
git commit -m "feat(android): add role selection and device pairing screens"
```

---

## Phase 3: AI Danger Detection

### Task 11: Implement DangerDetector with YAMNet

**Files:**
- Create: `app/app/src/main/java/com/formykids/child/DangerDetector.kt`
- Create: `app/app/src/test/java/com/formykids/child/DangerDetectorTest.kt`

- [ ] **Step 1: Write the failing unit test**

```kotlin
// app/app/src/test/java/com/formykids/child/DangerDetectorTest.kt
package com.formykids.child

import org.junit.Assert.*
import org.junit.Test

class DangerDetectorTest {

    @Test
    fun `normalizeAudio converts 16-bit PCM to float32`() {
        val pcm = byteArrayOf(0x00, 0x40) // 16384 as little-endian short
        val floats = DangerDetector.normalizeAudio(pcm)
        assertEquals(1, floats.size)
        assertEquals(16384f / Short.MAX_VALUE, floats[0], 0.001f)
    }

    @Test
    fun `normalizeAudio returns empty for too-short input`() {
        val result = DangerDetector.normalizeAudio(byteArrayOf(0x01))
        assertEquals(0, result.size)
    }

    @Test
    fun `isDangerClass identifies known danger labels`() {
        assertTrue(DangerDetector.isDangerLabel("Screaming"))
        assertTrue(DangerDetector.isDangerLabel("Crying, sobbing"))
        assertTrue(DangerDetector.isDangerLabel("Baby cry, infant cry"))
        assertTrue(DangerDetector.isDangerLabel("Shout"))
        assertFalse(DangerDetector.isDangerLabel("Speech"))
        assertFalse(DangerDetector.isDangerLabel("Music"))
    }
}
```

- [ ] **Step 2: Run test to confirm failure**

```bash
cd app && ./gradlew test --tests "com.formykids.child.DangerDetectorTest"
```

Expected: FAIL (class not found)

- [ ] **Step 3: Implement DangerDetector.kt**

```kotlin
package com.formykids.child

import android.content.Context
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DangerDetector(context: Context, private val confidenceThreshold: Float = 0.6f) : AutoCloseable {

    private val interpreter: Interpreter
    private val labels: List<String>

    init {
        val model = FileUtil.loadMappedFile(context, "yamnet.tflite")
        interpreter = Interpreter(model)
        labels = FileUtil.loadLabels(context, "yamnet_labels.txt")
    }

    data class Detection(val label: String, val confidence: Float, val type: String)

    fun analyze(pcmBytes: ByteArray): Detection? {
        val floats = normalizeAudio(pcmBytes)
        if (floats.size < SAMPLE_SIZE) return null

        val input = Array(1) { floats.copyOf(SAMPLE_SIZE) }
        val outputScores = Array(1) { FloatArray(labels.size) }
        interpreter.run(input, outputScores)

        val scores = outputScores[0]
        val maxIdx = scores.indices.maxByOrNull { scores[it] } ?: return null
        val maxScore = scores[maxIdx]
        val label = labels.getOrNull(maxIdx) ?: return null

        if (maxScore < confidenceThreshold || !isDangerLabel(label)) return null
        return Detection(label, maxScore, labelToType(label))
    }

    override fun close() = interpreter.close()

    companion object {
        const val SAMPLE_RATE = 16000
        const val SAMPLE_SIZE = 15600 // ~0.975 sec at 16kHz

        private val DANGER_LABELS = setOf(
            "Screaming", "Shout", "Bellow", "Whoop",
            "Crying, sobbing", "Baby cry, infant cry", "Whimper, whine",
            "Wail, moan"
        )

        fun normalizeAudio(pcmBytes: ByteArray): FloatArray {
            if (pcmBytes.size < 2) return FloatArray(0)
            val buf = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
            return FloatArray(pcmBytes.size / 2) { buf.short.toFloat() / Short.MAX_VALUE }
        }

        fun isDangerLabel(label: String): Boolean = DANGER_LABELS.any { label.contains(it, ignoreCase = true) }

        private fun labelToType(label: String): String = when {
            label.contains("scream", true) || label.contains("shout", true) -> "scream"
            label.contains("cry", true) || label.contains("wail", true) || label.contains("whimper", true) -> "cry"
            else -> "loud"
        }
    }
}
```

Also download `yamnet_labels.txt` from TensorFlow Hub and place at `app/app/src/main/assets/yamnet_labels.txt`.

- [ ] **Step 4: Run test to confirm pass**

```bash
cd app && ./gradlew test --tests "com.formykids.child.DangerDetectorTest"
```

Expected: PASS (4 tests)

- [ ] **Step 5: Commit**

```bash
git add app/app/src/main/java/com/formykids/child/DangerDetector.kt \
        app/app/src/test/java/com/formykids/child/DangerDetectorTest.kt \
        app/app/src/main/assets/yamnet_labels.txt
git commit -m "feat(android): add YAMNet-based DangerDetector with unit tests"
```

---

### Task 12: Integrate DangerDetector into AudioStreamService

**Files:**
- Modify: `app/app/src/main/java/com/formykids/child/AudioStreamService.kt`

- [ ] **Step 1: Update AudioStreamService to record at 16kHz and run DangerDetector**

Key changes: sampleRate 8000→16000, chunk size updated, add DangerDetector, add parallel analysis coroutine.

```kotlin
private var dangerDetector: DangerDetector? = null

override fun onCreate() {
    super.onCreate()
    dangerDetector = DangerDetector(this)
    startForeground(NOTIFICATION_ID, buildNotification())
    setupWebSocket()
}

override fun onDestroy() {
    stopStreaming()
    dangerDetector?.close()
    dangerDetector = null
    WebSocketManager.disconnect()
    scope.cancel()
    super.onDestroy()
}

private suspend fun captureLoop() {
    val sampleRate = 16000
    val minBuf = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
    val bufSize = maxOf(minBuf, 6400)
    val recorder = AudioRecord(
        MediaRecorder.AudioSource.MIC, sampleRate,
        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize
    )
    recorder.startRecording()
    // 100ms at 16kHz 16-bit = 3200 bytes
    val chunk = ByteArray(3200)
    // Accumulate ~1 second of audio for YAMNet (15600 samples = 31200 bytes)
    val analysisBuffer = ByteArray(31200)
    var analysisPos = 0
    var isPremium = false

    scope.launch { isPremium = com.formykids.FirestoreManager.isPremium() }

    try {
        while (streaming && currentCoroutineContext().isActive) {
            val read = recorder.read(chunk, 0, chunk.size)
            if (read > 0) {
                val data = chunk.copyOf(read)
                WebSocketManager.send(data)

                if (isPremium) {
                    val toCopy = minOf(read, analysisBuffer.size - analysisPos)
                    System.arraycopy(data, 0, analysisBuffer, analysisPos, toCopy)
                    analysisPos += toCopy
                    if (analysisPos >= analysisBuffer.size) {
                        analysisPos = 0
                        val detection = dangerDetector?.analyze(analysisBuffer)
                        if (detection != null) {
                            WebSocketManager.send(
                                """{"type":"danger_alert","class":"${detection.type}","confidence":${detection.confidence}}"""
                            )
                        }
                    }
                }
            }
        }
    } finally {
        recorder.stop()
        recorder.release()
    }
}
```

- [ ] **Step 2: Handle `stream_limit_reached` from server**

In `setupWebSocket()` `onTextMessage` handler, add:

```kotlin
"stream_limit_reached" -> {
    stopStreaming()
    statusCallback?.invoke(false)
    limitCallback?.invoke()
}
```

Add companion: `var limitCallback: (() -> Unit)? = null`

- [ ] **Step 3: Build**

```bash
cd app && ./gradlew compileDebugKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/app/src/main/java/com/formykids/child/AudioStreamService.kt
git commit -m "feat(android): integrate DangerDetector at 16kHz, handle stream_limit_reached"
```

---

## Phase 4: FCM Push Notifications

### Task 13: Add FCM Service to Android

**Files:**
- Create: `app/app/src/main/java/com/formykids/FcmService.kt`
- Modify: `app/app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Create FcmService.kt**

```kotlin
package com.formykids

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.formykids.parent.ParentActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FcmService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        CoroutineScope(Dispatchers.IO).launch {
            FirestoreManager.updateFcmToken(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: "위험 감지"
        val body = message.notification?.body ?: ""
        val intent = Intent(this, ParentActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("from_alert", true)
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, App.DANGER_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java)
            .notify(System.currentTimeMillis().toInt(), notification)
    }
}
```

- [ ] **Step 2: Register FcmService in AndroidManifest.xml**

Inside `<application>`:

```xml
<service
    android:name=".FcmService"
    android:exported="false">
    <intent-filter>
        <action android:name="com.google.firebase.MESSAGING_EVENT" />
    </intent-filter>
</service>
```

- [ ] **Step 3: Update FCM token on app start in SplashActivity.kt**

After Firebase Auth check in `SplashActivity.onCreate`, add:

```kotlin
com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
    lifecycleScope.launch { FirestoreManager.updateFcmToken(token) }
}
```

- [ ] **Step 4: Build**

```bash
cd app && ./gradlew compileDebugKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/app/src/main/java/com/formykids/FcmService.kt app/app/src/main/AndroidManifest.xml \
        app/app/src/main/java/com/formykids/SplashActivity.kt
git commit -m "feat(android): add FCM service for danger alert push notifications"
```

---

## Phase 5: Google Play Billing

### Task 14: Implement BillingManager

**Files:**
- Create: `app/app/src/main/java/com/formykids/billing/BillingManager.kt`
- Create: `app/app/src/test/java/com/formykids/billing/BillingManagerTest.kt`

- [ ] **Step 1: Write failing test (pure logic, no BillingClient)**

```kotlin
// app/app/src/test/java/com/formykids/billing/BillingManagerTest.kt
package com.formykids.billing

import org.junit.Assert.*
import org.junit.Test

class BillingManagerTest {

    @Test
    fun `isSubscriptionActive returns false for null expiry`() {
        assertFalse(BillingManager.isSubscriptionActive(null))
    }

    @Test
    fun `isSubscriptionActive returns false for past expiry`() {
        assertFalse(BillingManager.isSubscriptionActive(1000L))
    }

    @Test
    fun `isSubscriptionActive returns true for future expiry`() {
        assertTrue(BillingManager.isSubscriptionActive(System.currentTimeMillis() + 86400_000L))
    }
}
```

- [ ] **Step 2: Run test to confirm failure**

```bash
cd app && ./gradlew test --tests "com.formykids.billing.BillingManagerTest"
```

Expected: FAIL

- [ ] **Step 3: Implement BillingManager.kt**

```kotlin
package com.formykids.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import com.formykids.FirestoreManager
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class BillingManager(context: Context, private val serverUrl: String) : PurchasesUpdatedListener {

    private val client = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases()
        .build()

    var onPurchaseSuccess: (() -> Unit)? = null
    var onPurchaseError: ((String) -> Unit)? = null

    fun connect(onReady: () -> Unit) {
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) onReady()
            }
            override fun onBillingServiceDisconnected() {}
        })
    }

    suspend fun queryAndLaunch(activity: Activity) {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(PRODUCT_ID)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            )).build()
        val result = client.queryProductDetails(params)
        val details = result.productDetailsList?.firstOrNull() ?: run {
            onPurchaseError?.invoke("상품 정보를 불러올 수 없습니다"); return
        }
        val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: return
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(details)
                    .setOfferToken(offerToken)
                    .build()
            )).build()
        client.launchBillingFlow(activity, flowParams)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        if (result.responseCode != BillingClient.BillingResponseCode.OK || purchases == null) {
            onPurchaseError?.invoke("결제 실패 (${result.responseCode})")
            return
        }
        purchases.forEach { purchase ->
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                CoroutineScope(Dispatchers.IO).launch {
                    verifyWithServer(purchase.purchaseToken)
                    client.acknowledgePurchase(
                        AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
                    ) {}
                }
            }
        }
    }

    private suspend fun verifyWithServer(purchaseToken: String) {
        val idToken = Firebase.auth.currentUser?.getIdToken(false)?.await()?.token ?: return
        val body = JSONObject(mapOf("idToken" to idToken, "purchaseToken" to purchaseToken))
            .toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url("$serverUrl/verify-purchase").post(body).build()
        runCatching { OkHttpClient().newCall(request).execute() }.onSuccess {
            if (it.isSuccessful) onPurchaseSuccess?.invoke()
            else onPurchaseError?.invoke("서버 검증 실패")
        }
    }

    fun release() = client.endConnection()

    companion object {
        const val PRODUCT_ID = "premium_monthly"

        fun isSubscriptionActive(expiresAtMs: Long?): Boolean {
            if (expiresAtMs == null) return false
            return expiresAtMs > System.currentTimeMillis()
        }
    }
}
```

- [ ] **Step 4: Run test to confirm pass**

```bash
cd app && ./gradlew test --tests "com.formykids.billing.BillingManagerTest"
```

Expected: PASS (3 tests)

- [ ] **Step 5: Commit**

```bash
git add app/app/src/main/java/com/formykids/billing/BillingManager.kt \
        app/app/src/test/java/com/formykids/billing/BillingManagerTest.kt
git commit -m "feat(android): add BillingManager with Play Billing v7 and server verification"
```

---

### Task 15: Build PaywallActivity

**Files:**
- Create: `app/app/src/main/java/com/formykids/billing/PaywallActivity.kt`

- [ ] **Step 1: Create PaywallActivity.kt**

```kotlin
package com.formykids.billing

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.formykids.App
import com.formykids.databinding.ActivityPaywallBinding
import kotlinx.coroutines.launch

class PaywallActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPaywallBinding
    private lateinit var billing: BillingManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaywallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = getSharedPreferences(App.PREF_NAME, MODE_PRIVATE)
        val serverUrl = prefs.getString(App.PREF_SERVER_URL, App.DEFAULT_SERVER_URL)!!

        billing = BillingManager(this, serverUrl)
        billing.onPurchaseSuccess = {
            runOnUiThread {
                Toast.makeText(this, "구독이 시작되었습니다!", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            }
        }
        billing.onPurchaseError = { msg ->
            runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
        }

        billing.connect {
            binding.btnSubscribe.isEnabled = true
        }

        binding.btnSubscribe.setOnClickListener {
            lifecycleScope.launch { billing.queryAndLaunch(this@PaywallActivity) }
        }

        binding.btnClose.setOnClickListener { finish() }
    }

    override fun onDestroy() {
        billing.release()
        super.onDestroy()
    }
}
```

Note: `activity_paywall.xml` needs: feature comparison table/text, `btnSubscribe` (Button, initially disabled), `btnClose` (Button).

- [ ] **Step 2: Add to AndroidManifest.xml**

```xml
<activity android:name=".billing.PaywallActivity" android:exported="false" />
```

- [ ] **Step 3: Build**

```bash
cd app && ./gradlew compileDebugKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/app/src/main/java/com/formykids/billing/PaywallActivity.kt app/app/src/main/AndroidManifest.xml
git commit -m "feat(android): add PaywallActivity for premium subscription"
```

---

## Phase 6: UX Polish

### Task 16: Build SettingsActivity

**Files:**
- Create: `app/app/src/main/java/com/formykids/settings/SettingsActivity.kt`

- [ ] **Step 1: Create SettingsActivity.kt**

```kotlin
package com.formykids.settings

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.formykids.App
import com.formykids.SplashActivity
import com.formykids.databinding.ActivitySettingsBinding
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = getSharedPreferences(App.PREF_NAME, MODE_PRIVATE)

        binding.etServerUrl.setText(prefs.getString(App.PREF_SERVER_URL, App.DEFAULT_SERVER_URL))

        binding.btnSaveUrl.setOnClickListener {
            prefs.edit().putString(App.PREF_SERVER_URL, binding.etServerUrl.text.toString().trim()).apply()
            finish()
        }

        binding.btnLogout.setOnClickListener {
            Firebase.auth.signOut()
            prefs.edit().clear().apply()
            startActivity(Intent(this, SplashActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        }

        binding.btnManageSubscription.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                data = android.net.Uri.parse("https://play.google.com/store/account/subscriptions")
            })
        }
    }
}
```

- [ ] **Step 2: Add to AndroidManifest.xml and add settings button to ParentActivity + ChildActivity**

In AndroidManifest.xml:
```xml
<activity android:name=".settings.SettingsActivity" android:exported="false" />
```

In `ParentActivity.kt` and `ChildActivity.kt`, add settings button handler:
```kotlin
binding.btnSettings.setOnClickListener {
    startActivity(Intent(this, SettingsActivity::class.java))
}
```

- [ ] **Step 3: Build**

```bash
cd app && ./gradlew compileDebugKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/app/src/main/java/com/formykids/settings/ app/app/src/main/AndroidManifest.xml \
        app/app/src/main/java/com/formykids/parent/ParentActivity.kt \
        app/app/src/main/java/com/formykids/child/ChildActivity.kt
git commit -m "feat(android): add SettingsActivity with server URL, logout, subscription management"
```

---

### Task 17: Build AlertHistoryFragment

**Files:**
- Create: `app/app/src/main/java/com/formykids/parent/AlertHistoryFragment.kt`
- Modify: `app/app/src/main/java/com/formykids/parent/ParentActivity.kt`

- [ ] **Step 1: Create AlertHistoryFragment.kt**

```kotlin
package com.formykids.parent

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.formykids.FirestoreManager
import com.formykids.databinding.FragmentAlertHistoryBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlertHistoryFragment : Fragment() {
    private var _binding: FragmentAlertHistoryBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAlertHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        lifecycleScope.launch {
            val user = FirestoreManager.getCurrentUser() ?: return@launch
            val familyId = user["familyId"] as? String ?: return@launch
            val alerts = FirestoreManager.getAlerts(familyId)
            val fmt = SimpleDateFormat("MM/dd HH:mm", Locale.KOREA)
            val items = alerts.map { alert ->
                val ts = (alert["timestamp"] as? Long) ?: 0L
                val type = when (alert["type"]) { "scream" -> "비명"; "cry" -> "울음"; else -> "큰소리" }
                val conf = ((alert["confidence"] as? Double)?.times(100))?.toInt() ?: 0
                "${fmt.format(Date(ts))}  $type (${conf}%)"
            }
            binding.listAlerts.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, items)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
```

Note: `fragment_alert_history.xml` needs a `ListView` with id `listAlerts`.

- [ ] **Step 2: Add tab/button to ParentActivity to show AlertHistoryFragment**

In `ParentActivity.kt`, add:

```kotlin
binding.btnAlertHistory.setOnClickListener {
    supportFragmentManager.beginTransaction()
        .replace(R.id.fragmentContainer, AlertHistoryFragment())
        .addToBackStack(null)
        .commit()
}
```

- [ ] **Step 3: Build**

```bash
cd app && ./gradlew compileDebugKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Run all unit tests**

```bash
cd app && ./gradlew test
```

Expected: All pass

- [ ] **Step 5: Final commit**

```bash
git add app/app/src/main/java/com/formykids/parent/AlertHistoryFragment.kt \
        app/app/src/main/java/com/formykids/parent/ParentActivity.kt
git commit -m "feat(android): add alert history screen for parents"
```

---

## Post-Implementation Checklist

- [ ] Firebase Console: enable Phone Auth, create Firestore indexes (alerts.familyId + timestamp)
- [ ] Google Play Console: create `premium_monthly` subscription product
- [ ] Cloud Run: confirm `/health` responds, WebSocket connects from device
- [ ] End-to-end test: child streams audio → parent hears → trigger loud sound → parent receives FCM push
- [ ] End-to-end test: free user hits 30-minute limit → paywall appears → subscribe → limit lifted
- [ ] Firestore Security Rules: lock down read/write to authenticated users' own documents

### Firestore Security Rules (add in Firebase Console)

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{uid} {
      allow read, write: if request.auth.uid == uid;
    }
    match /subscriptions/{uid} {
      allow read: if request.auth.uid == uid;
      allow write: if false; // server-side only
    }
    match /families/{familyId} {
      allow read: if request.auth != null &&
        resource.data.parentUids.hasAny([request.auth.uid]) ||
        resource.data.childUid == request.auth.uid;
      allow write: if request.auth != null;
    }
    match /alerts/{alertId} {
      allow read: if request.auth != null &&
        get(/databases/$(database)/documents/users/$(request.auth.uid)).data.familyId == resource.data.familyId;
      allow write: if false; // server-side only
    }
  }
}
```
