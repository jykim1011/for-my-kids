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
