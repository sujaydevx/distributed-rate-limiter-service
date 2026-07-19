package com.sujay.ratelimiter.service;

import com.sujay.ratelimiter.config.RateLimiterProperties;
import com.sujay.ratelimiter.model.RateLimitResult;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class SlidingWindowRateLimiter implements RateLimiterService {

    private static final String KEY_PREFIX = "ratelimit:sliding:";
    private static final String ALGORITHM = "SLIDING_WINDOW";

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<List> slidingWindowScript;
    private final RateLimiterProperties properties;
    private final MeterRegistry meterRegistry;
    private final CircuitBreaker circuitBreaker;

    public SlidingWindowRateLimiter(StringRedisTemplate redisTemplate,
                                    DefaultRedisScript<List> slidingWindowScript,
                                    RateLimiterProperties properties,
                                    MeterRegistry meterRegistry,
                                    CircuitBreakerRegistry circuitBreakerRegistry) {
        this.redisTemplate = redisTemplate;
        this.slidingWindowScript = slidingWindowScript;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("redisRateLimiter");
    }

    @Override
    @SuppressWarnings("unchecked")
    public RateLimitResult isAllowed(String clientId) {
        String key = KEY_PREFIX + clientId;
        int maxRequests = properties.slidingWindow().maxRequests();
        int windowMs = properties.slidingWindow().windowSeconds() * 1000;

        try {
            List<Long> result = circuitBreaker.executeSupplier(() ->
                    (List<Long>) redisTemplate.execute(
                            slidingWindowScript,
                            Collections.singletonList(key),
                            String.valueOf(maxRequests),
                            String.valueOf(windowMs),
                            String.valueOf(System.currentTimeMillis())
                    )
            );

            boolean allowed = result.get(0) == 1L;
            long remaining = result.get(1);
            long retryAfterMs = result.get(2);

            recordDecision(allowed, false);

            return new RateLimitResult(allowed, remaining, maxRequests, retryAfterMs / 1000, ALGORITHM);

        } catch (Exception e) {
            return handleFailure(e, maxRequests);
        }
    }

    private RateLimitResult handleFailure(Exception e, int maxRequests) {
        boolean circuitOpen = e instanceof CallNotPermittedException;
        System.err.println("[SlidingWindowRateLimiter] Redis unavailable (circuitOpen=" + circuitOpen + "): " + e.getMessage());
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