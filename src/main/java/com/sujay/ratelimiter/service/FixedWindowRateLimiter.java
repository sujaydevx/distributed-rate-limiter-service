package com.sujay.ratelimiter.service;


import com.sujay.ratelimiter.config.RateLimiterProperties;
import com.sujay.ratelimiter.model.RateLimitResult;
import com.sujay.ratelimiter.service.RateLimiterService;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class FixedWindowRateLimiter implements RateLimiterService {

    private static final String KEY_PREFIX = "ratelimit:fixed:";
    private static final String ALGORITHM = "FIXED_WINDOW";

    private final StringRedisTemplate redisTemplate;
    private final RateLimiterProperties properties;
    private final MeterRegistry meterRegistry;
    private final CircuitBreaker circuitBreaker;

    public FixedWindowRateLimiter(StringRedisTemplate redisTemplate,
                                  RateLimiterProperties properties,
                                  MeterRegistry meterRegistry,
                                  CircuitBreakerRegistry circuitBreakerRegistry) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("redisRateLimiter");
    }

    @Override
    public RateLimitResult isAllowed(String clientId) {
        String key = KEY_PREFIX + clientId;
        int maxRequests = properties.fixedWindow().maxRequests();
        int windowSeconds = properties.fixedWindow().windowSeconds();

        try {
            Long count = circuitBreaker.executeSupplier(() ->
                    redisTemplate.opsForValue().increment(key)
            );

            if (count != null && count == 1L) {
                redisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
            }

            long secondsLeftInWindow = redisTemplate.getExpire(key);
            if (secondsLeftInWindow < 0) {
                secondsLeftInWindow = windowSeconds;
            }

            boolean allowed = count != null && count <= maxRequests;
            recordDecision(allowed, false);

            long remaining = allowed ? Math.max(0, maxRequests - count) : 0;
            long retryAfterSeconds = allowed ? 0 : secondsLeftInWindow;

            return new RateLimitResult(allowed, remaining, maxRequests, retryAfterSeconds, ALGORITHM);

        } catch (Exception e) {
            return handleFailure(e, maxRequests);
        }
    }

    private RateLimitResult handleFailure(Exception e, int maxRequests) {
        boolean circuitOpen = e instanceof CallNotPermittedException;
        System.err.println("[FixedWindowRateLimiter] Redis unavailable (circuitOpen=" + circuitOpen + "): " + e.getMessage());
        recordDecision(true, circuitOpen);
        return new RateLimitResult(true, maxRequests, maxRequests, 0, ALGORITHM);
    }

    private void recordDecision(boolean allowed, boolean circuitOpen) {
        meterRegistry.counter("ratelimiter_decisions_total",
                "algorithm", ALGORITHM,
                "decision", allowed ? "allowed" : "denied",
                "circuit_open", String.valueOf(circuitOpen)
        ).increment();
    }
}