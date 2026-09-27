package com.geekyants.assessment.infra.redis;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Enterprise distributed rate limiter backed by Redis.
 * Uses a sliding-window counter to prevent DDoS, brute-force, or submission spam.
 */
@Service
public class RedisRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);
    private final StringRedisTemplate redis;

    public RedisRateLimiter(@org.springframework.beans.factory.annotation.Autowired(required = false) StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Checks if a key (e.g. client IP or candidate ID) is allowed within a rate limit window.
     *
     * @param key Key to rate-limit (e.g., "ratelimit:intake:192.168.1.1")
     * @param maxRequests Maximum requests allowed in the window
     * @param window Duration of the rate-limit window
     * @return true if request is allowed, false if limit exceeded
     */
    public boolean allowRequest(String key, int maxRequests, Duration window) {
        if (redis == null) {
            return true;
        }
        try {
            Long current = redis.opsForValue().increment(key);
            if (current != null && current == 1) {
                redis.expire(key, window);
            }
            if (current != null && current > maxRequests) {
                log.warn("Rate limit exceeded for key={}: count={}, limit={}", key, current, maxRequests);
                return false;
            }
            return true;
        } catch (Exception e) {
            // Graceful degradation: if Redis is temporarily unreachable, allow request and log warning
            log.warn("Redis rate limiter check failed ({}); allowing request as graceful fallback", e.getMessage());
            return true;
        }
    }
}
