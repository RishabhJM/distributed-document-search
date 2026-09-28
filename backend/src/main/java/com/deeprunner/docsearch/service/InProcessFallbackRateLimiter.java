package com.deeprunner.docsearch.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Coarse in-memory token bucket used when Redis is degraded or unreachable.
 * Allows graceful degradation without completely removing abuse boundaries.
 */
@Component
public class InProcessFallbackRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(InProcessFallbackRateLimiter.class);

    private static class LocalBucket {
        double tokens;
        long lastRefreshed;

        LocalBucket(double initialTokens, long now) {
            this.tokens = initialTokens;
            this.lastRefreshed = now;
        }
    }

    private final ConcurrentMap<String, LocalBucket> buckets = new ConcurrentHashMap<>();

    @Override
    public RateLimitResult tryConsume(String tenantId, int rateLimitPerSecond) {
        long now = System.currentTimeMillis();
        // Fallback capacity has a 20% buffer
        double capacity = Math.max(1, rateLimitPerSecond * 1.2);
        double refillRate = Math.max(1, rateLimitPerSecond);

        LocalBucket bucket = buckets.compute(tenantId, (k, existing) -> {
            if (existing == null) {
                return new LocalBucket(capacity - 1, now);
            }
            long delta = Math.max(0, now - existing.lastRefreshed);
            double added = (delta / 1000.0) * refillRate;
            existing.tokens = Math.min(capacity, existing.tokens + added);
            existing.lastRefreshed = now;
            return existing;
        });

        synchronized (bucket) {
            if (bucket.tokens >= 1.0) {
                bucket.tokens -= 1.0;
                return RateLimitResult.allow((long) bucket.tokens, (long) capacity);
            } else {
                long waitSec = (long) Math.ceil((1.0 - bucket.tokens) / refillRate);
                log.warn("Fallback rate limiter throttled tenant '{}'", tenantId);
                return RateLimitResult.reject(Math.max(1, waitSec), (long) capacity);
            }
        }
    }
}
