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
