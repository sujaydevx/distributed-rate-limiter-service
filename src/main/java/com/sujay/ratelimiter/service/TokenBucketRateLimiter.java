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
public class TokenBucketRateLimiter implements RateLimiterService {

    private static final String KEY_PREFIX = "ratelimit:bucket:";
    private static final String ALGORITHM = "TOKEN_BUCKET";

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<List> tokenBucketScript;
    private final RateLimiterProperties properties;
    private final MeterRegistry meterRegistry;
    private final CircuitBreaker circuitBreaker;

    public TokenBucketRateLimiter(StringRedisTemplate redisTemplate,
                                  DefaultRedisScript<List> tokenBucketScript,
                                  RateLimiterProperties properties,
                                  MeterRegistry meterRegistry,
                                  CircuitBreakerRegistry circuitBreakerRegistry) {
        this.redisTemplate = redisTemplate;
        this.tokenBucketScript = tokenBucketScript;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("redisRateLimiter");
    }

    @Override
    @SuppressWarnings("unchecked")
    public RateLimitResult isAllowed(String clientId) {
        String key = KEY_PREFIX + clientId;
        int capacity = properties.tokenBucket().capacity();
        double refillRate = properties.tokenBucket().refillRate();

        try {
            List<Long> result = circuitBreaker.executeSupplier(() ->
                    (List<Long>) redisTemplate.execute(
                            tokenBucketScript,
                            Collections.singletonList(key),
                            String.valueOf(capacity),
                            String.valueOf(refillRate),
                            String.valueOf(System.currentTimeMillis())
                    )
            );

            boolean allowed = result.get(0) == 1L;
            long remainingTokens = result.get(1);
            long retryAfterMs = result.get(2);

            recordDecision(allowed, false);

            return new RateLimitResult(allowed, remainingTokens, capacity, retryAfterMs / 1000, ALGORITHM);

        } catch (Exception e) {
            return handleFailure(e, capacity);
        }
    }

    private RateLimitResult handleFailure(Exception e, int capacity) {
        boolean circuitOpen = e instanceof CallNotPermittedException;
        System.err.println("[TokenBucketRateLimiter] Redis unavailable (circuitOpen=" + circuitOpen + "): " + e.getMessage());
        recordDecision(true, circuitOpen);
        return new RateLimitResult(true, capacity, capacity, 0, ALGORITHM);
    }

    private void recordDecision(boolean allowed, boolean circuitOpen) {
        meterRegistry.counter("ratelimiter_decisions_total",
                "algorithm", ALGORITHM,
                "decision", allowed ? "allowed" : "denied",
                "circuit_open", String.valueOf(circuitOpen)
        ).increment();
    }
}