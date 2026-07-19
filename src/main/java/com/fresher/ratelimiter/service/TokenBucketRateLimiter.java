package com.fresher.ratelimiter.service;

import com.fresher.ratelimiter.config.RateLimiterProperties;
import com.fresher.ratelimiter.model.RateLimitResult;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * TOKEN BUCKET — a smoother alternative to fixed window.
 *
 * Idea: each client has a "bucket" that holds up to `capacity` tokens. Every
 * request costs 1 token. Tokens refill continuously over time at `refillRate`
 * tokens/second, up to the capacity. If the bucket is empty, deny the request.
 *
 * Why this is better than fixed window for bursty-but-legitimate traffic: a user
 * loading a page that fires 5 API calls at once can spend up to `capacity` tokens
 * immediately (a burst), but can never sustain more than `refillRate` requests
 * per second on average over time — there's no window boundary to exploit.
 *
 * Trade-off vs fixed window: more state to store per client (two fields instead
 * of one counter) and the logic genuinely needs to run atomically inside Redis
 * (see token_bucket.lua) instead of a single built-in command like INCR.
 */
@Service
public class TokenBucketRateLimiter implements RateLimiterService {

    private static final String KEY_PREFIX = "ratelimit:bucket:";

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<List> tokenBucketScript;
    private final RateLimiterProperties properties;
    private final MeterRegistry meterRegistry;

    public TokenBucketRateLimiter(StringRedisTemplate redisTemplate,
                                   DefaultRedisScript<List> tokenBucketScript,
                                   RateLimiterProperties properties,
                                   MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.tokenBucketScript = tokenBucketScript;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    @Override
    @SuppressWarnings("unchecked")
    public RateLimitResult isAllowed(String clientId) {
        String key = KEY_PREFIX + clientId;
        int capacity = properties.tokenBucket().capacity();
        double refillRate = properties.tokenBucket().refillRate();

        try {
            List<Long> result = redisTemplate.execute(
                    tokenBucketScript,
                    Collections.singletonList(key),
                    String.valueOf(capacity),
                    String.valueOf(refillRate),
                    String.valueOf(System.currentTimeMillis())
            );

            boolean allowed = result.get(0) == 1L;
            long remainingTokens = result.get(1);
            long retryAfterMs = result.get(2);

            recordDecision(allowed);

            return new RateLimitResult(allowed, remainingTokens, retryAfterMs / 1000);

        } catch (Exception e) {
            // Same fail-open choice as FixedWindowRateLimiter — see the comment there.
            System.err.println("[TokenBucketRateLimiter] Redis error, failing open: " + e.getMessage());
            return new RateLimitResult(true, capacity, 0);
        }
    }

    private void recordDecision(boolean allowed) {
        meterRegistry.counter("ratelimiter_decisions_total",
                "algorithm", "TOKEN_BUCKET",
                "decision", allowed ? "allowed" : "denied"
        ).increment();
    }
}
