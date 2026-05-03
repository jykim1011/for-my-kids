require('dotenv').config();
const express = require('express');
const http = require('http');
const WebSocket = require('ws');
const { getOrCreate, removeClient, sessions } = require('./sessions');
const { verifyIdToken, getFirestore, getMessaging } = require('./auth');
const { sendDangerAlert, TYPE_LABELS } = require('./fcm');
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
    // paymentState: 0=pending, 1=received, 2=free trial
    const paymentState = data.paymentState;
    if ((paymentState !== 1 && paymentState !== 2) || expiresAt <= Date.now()) {
      return res.status(400).json({ error: 'subscription not active' });
    }
    await getFirestore().collection('subscriptions').doc(uid).set(
      { plan: 'premium', expiresAt, purchaseToken },
      { merge: true }
    );
    res.json({ ok: true, expiresAt });
  } catch (e) {
    res.status(400).json({ error: e.message });
  }
});

app.post('/alert', async (req, res) => {
  const { idToken, type, confidence, familyId } = req.body;
  if (!idToken || !type || !familyId || confidence == null) return res.status(400).json({ error: 'missing fields' });
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

const server = http.createServer(app);
const wss = new WebSocket.Server({ server });

const wsClients = new Map();

function safeSend(ws, data) {
  if (ws && ws.readyState === WebSocket.OPEN) ws.send(data);
}

async function flushChildSeconds(familyId) {
  const session = sessions.get(familyId);
  if (!session || !session.childUid || !session.pendingFlush) return;
  session.pendingFlush = false;
  try {
    await getFirestore().collection('subscriptions').doc(session.childUid).set(
      { dailyStreamedSeconds: session.dailyStreamedSeconds, dailyResetAt: session.dailyResetAt },
      { merge: true }
    );
  } catch (e) {
    console.error('flush error:', e.message);
  }
}

// Flush streaming seconds to Firestore every 60 seconds
setInterval(async () => {
  for (const [familyId] of sessions) {
    await flushChildSeconds(familyId);
  }
}, 60000);

wss.on('connection', (ws) => {
  ws.on('message', async (data, isBinary) => {
    if (isBinary) {
      const meta = wsClients.get(ws);
      if (!meta || meta.role !== 'child') return;
      const session = getOrCreate(meta.familyId);

      if (session.plan !== 'premium') {
        const now = Date.now();
        if (now - session.dailyResetAt > 86400000) {
          session.dailyStreamedSeconds = 0;
          session.dailyResetAt = now;
        }
        if (session.dailyStreamedSeconds >= FREE_DAILY_LIMIT_SECONDS) {
          safeSend(ws, JSON.stringify({ type: 'stream_limit_reached' }));
          return;
        }
        session.dailyStreamedSeconds += 0.1;
        session.pendingFlush = true;
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
          session.childUid = uid;
          if (session.listeningParents.size > 0) {
            safeSend(ws, JSON.stringify({ type: 'start_stream' }));
          }
          // Load streaming quota from Firestore into memory
          const subDoc = await getFirestore().collection('subscriptions').doc(uid).get();
          const sub = subDoc.data() ?? {};
          const now = Date.now();
          if (now - (sub.dailyResetAt ?? 0) > 86400000) {
            session.dailyStreamedSeconds = 0;
            session.dailyResetAt = now;
          } else {
            session.dailyStreamedSeconds = sub.dailyStreamedSeconds ?? 0;
            session.dailyResetAt = sub.dailyResetAt ?? now;
          }
          session.plan = sub.plan === 'premium' && sub.expiresAt > now ? 'premium' : 'free';
          session.pendingFlush = false;
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
      // Only premium children can trigger FCM danger alerts
      if (session.plan !== 'premium') return;
      const parentTokens = await getParentFcmTokens(meta.familyId);
      await Promise.all(parentTokens.map(token =>
        sendDangerAlert({ fcmToken: token, type: msg.class, confidence: msg.confidence, familyId: meta.familyId })
      ));
    }
  });

  ws.on('close', async () => {
    const result = removeClient(ws);
    wsClients.delete(ws);
    if (!result) return;
    if (result.role === 'child') {
      await flushChildSeconds(result.familyId);
    } else if (result.wasListening) {
      const session = sessions.get(result.familyId);
      if (session && session.listeningParents.size === 0) {
        safeSend(session.child, JSON.stringify({ type: 'stop_stream' }));
      }
    }
    // Prune empty sessions
    const session = sessions.get(result.familyId);
    if (session && !session.child && session.parents.size === 0) {
      sessions.delete(result.familyId);
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
