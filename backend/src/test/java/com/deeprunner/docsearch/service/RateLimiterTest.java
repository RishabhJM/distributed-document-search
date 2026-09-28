package com.deeprunner.docsearch.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterTest {

    @Test
    @DisplayName("InProcessFallbackRateLimiter respects capacity and throttles when depleted")
    void testFallbackRateLimiting() {
        InProcessFallbackRateLimiter limiter = new InProcessFallbackRateLimiter();
        String tenant = "test-tenant";
        int limit = 5;

        // Consume up to limit (with the 20% fallback buffer, capacity is 6)
        int allowedCount = 0;
        for (int i = 0; i < 10; i++) {
            RateLimiter.RateLimitResult res = limiter.tryConsume(tenant, limit);
            if (res.allowed()) {
                allowedCount++;
            }
        }

        assertTrue(allowedCount >= 5 && allowedCount <= 7,
            "Should allow requests up to capacity (expected ~6, got " + allowedCount + ")");

        // Subsequent requests should be rejected with reset wait time
        RateLimiter.RateLimitResult rejected = limiter.tryConsume(tenant, limit);
        assertFalse(rejected.allowed(), "Requests past capacity must be throttled");
        assertTrue(rejected.resetSeconds() >= 1, "Must indicate non-zero wait duration");
    }

    @Test
    @DisplayName("Tenants have completely isolated rate limit buckets")
    void testTenantBucketIsolation() {
        InProcessFallbackRateLimiter limiter = new InProcessFallbackRateLimiter();
        int limit = 2;

        // Deplete tenant A
        for (int i = 0; i < 10; i++) {
            limiter.tryConsume("tenant-a", limit);
        }

        assertFalse(limiter.tryConsume("tenant-a", limit).allowed(), "Tenant A should be throttled");

        // Tenant B should still be allowed
        RateLimiter.RateLimitResult resB = limiter.tryConsume("tenant-b", limit);
        assertTrue(resB.allowed(), "Tenant B must not be affected by Tenant A's usage");
    }
}
