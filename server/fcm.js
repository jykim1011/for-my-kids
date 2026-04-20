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
