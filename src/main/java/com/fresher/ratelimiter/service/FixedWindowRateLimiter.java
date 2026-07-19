package com.fresher.ratelimiter.service;

import com.fresher.ratelimiter.config.RateLimiterProperties;
import com.fresher.ratelimiter.model.RateLimitResult;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * FIXED WINDOW — the simplest possible rate limiter.
 *
 * Idea: count requests in a time bucket (e.g. "this 60-second window"). Once the
 * count passes the limit, deny until the window ends and the counter resets.
 *
 * Known trade-off (say this out loud in an interview, it shows you understand it):
 * a client can send their full limit right at the END of one window, then their
 * full limit again right at the START of the next window — two limits' worth of
 * requests in a very short span, even though the configured rate is "X per window."
 * That's the "boundary burst" problem. Token bucket (see the other implementation)
 * doesn't have this issue, at the cost of slightly more complex logic.
 */
@Service
public class FixedWindowRateLimiter implements RateLimiterService {

    private static final String KEY_PREFIX = "ratelimit:fixed:";

    private final StringRedisTemplate redisTemplate;
    private final RateLimiterProperties properties;
    private final MeterRegistry meterRegistry;

    public FixedWindowRateLimiter(StringRedisTemplate redisTemplate,
                                   RateLimiterProperties properties,
                                   MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public RateLimitResult isAllowed(String clientId) {
        String key = KEY_PREFIX + clientId;
        int maxRequests = properties.fixedWindow().maxRequests();
        int windowSeconds = properties.fixedWindow().windowSeconds();

        try {
            // INCR is atomic in Redis by itself — no Lua script required here.
            // Two requests arriving at the exact same instant still get counted
            // correctly because Redis processes commands one at a time.
            Long count = redisTemplate.opsForValue().increment(key);

            // Only the request that takes the count from 0 -> 1 sets the expiry.
            // This is what makes the counter reset after windowSeconds of the
            // FIRST request in that window, not a rolling timer.
            if (count != null && count == 1L) {
                redisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
            }

            long secondsLeftInWindow = redisTemplate.getExpire(key);
            if (secondsLeftInWindow < 0) {
                secondsLeftInWindow = windowSeconds; // safety fallback, shouldn't normally happen
            }

            boolean allowed = count != null && count <= maxRequests;
            recordDecision(allowed);

            long remaining = allowed ? Math.max(0, maxRequests - count) : 0;
            long retryAfterSeconds = allowed ? 0 : secondsLeftInWindow;

            return new RateLimitResult(allowed, remaining, retryAfterSeconds);

        } catch (Exception e) {
            // Redis is unreachable. We choose to FAIL OPEN — let the request through
            // rather than block every user because our infrastructure had a blip.
            // (A circuit breaker to make this smarter — fail fast instead of trying
            // Redis every single time — is a planned next step, not built yet.)
            System.err.println("[FixedWindowRateLimiter] Redis error, failing open: " + e.getMessage());
            return new RateLimitResult(true, maxRequests, 0);
        }
    }

    private void recordDecision(boolean allowed) {
        meterRegistry.counter("ratelimiter_decisions_total",
                "algorithm", "FIXED_WINDOW",
                "decision", allowed ? "allowed" : "denied"
        ).increment();
    }
}
