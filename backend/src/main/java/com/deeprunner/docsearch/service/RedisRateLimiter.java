package com.deeprunner.docsearch.service;

import com.deeprunner.docsearch.cache.CacheKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
@Primary
public class RedisRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);

    private final StringRedisTemplate redisTemplate;
    @SuppressWarnings("rawtypes")
    private final RedisScript<List> rateLimitScript;
    private final InProcessFallbackRateLimiter fallbackLimiter;

    @SuppressWarnings("rawtypes")
    public RedisRateLimiter(StringRedisTemplate redisTemplate,
                            RedisScript<List> rateLimitScript,
                            InProcessFallbackRateLimiter fallbackLimiter) {
        this.redisTemplate = redisTemplate;
        this.rateLimitScript = rateLimitScript;
        this.fallbackLimiter = fallbackLimiter;
    }

    @Override
    public RateLimitResult tryConsume(String tenantId, int rateLimitPerSecond) {
        String key = CacheKeys.rateLimitKey(tenantId);
        long now = System.currentTimeMillis();

        try {
            List<?> result = redisTemplate.execute(
                rateLimitScript,
                Collections.singletonList(key),
                String.valueOf(rateLimitPerSecond),
                String.valueOf(rateLimitPerSecond),
                "1",
                String.valueOf(now)
            );

            if (result != null && result.size() >= 3) {
                long allowed = ((Number) result.get(0)).longValue();
                long remaining = ((Number) result.get(1)).longValue();
                long waitSeconds = ((Number) result.get(2)).longValue();

                if (allowed == 1) {
                    return RateLimitResult.allow(remaining, rateLimitPerSecond);
                } else {
                    return RateLimitResult.reject(Math.max(1, waitSeconds), rateLimitPerSecond);
                }
            }
        } catch (Exception e) {
            log.warn("Redis rate limiter failed for tenant '{}' ({}); invoking in-process fallback",
                tenantId, e.getMessage());
            return fallbackLimiter.tryConsume(tenantId, rateLimitPerSecond);
        }

        return fallbackLimiter.tryConsume(tenantId, rateLimitPerSecond);
    }
}
