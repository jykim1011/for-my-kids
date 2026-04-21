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
