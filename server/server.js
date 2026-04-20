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
    if (result.role !== 'child' && result.wasListening) {
      const session = getOrCreate(result.familyId);
      if (session.listeningParents.size === 0) safeSend(session.child, JSON.stringify({ type: 'stop_stream' }));
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
