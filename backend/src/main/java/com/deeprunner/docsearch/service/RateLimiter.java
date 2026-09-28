package com.deeprunner.docsearch.service;

public interface RateLimiter {

    RateLimitResult tryConsume(String tenantId, int rateLimitPerSecond);

    record RateLimitResult(
        boolean allowed,
        long remainingTokens,
        long resetSeconds,
        long limit
    ) {
        public static RateLimitResult allow(long remainingTokens, long limit) {
            return new RateLimitResult(true, remainingTokens, 0, limit);
        }

        public static RateLimitResult reject(long resetSeconds, long limit) {
            return new RateLimitResult(false, 0, resetSeconds, limit);
        }
    }
}
