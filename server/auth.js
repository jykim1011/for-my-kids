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
