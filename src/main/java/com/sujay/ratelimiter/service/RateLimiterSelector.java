package com.sujay.ratelimiter.service;

import com.sujay.ratelimiter.config.RateLimiterProperties;
import com.sujay.ratelimiter.service.FixedWindowRateLimiter;
import com.sujay.ratelimiter.service.RateLimiterService;
import com.sujay.ratelimiter.service.SlidingWindowRateLimiter;
import com.sujay.ratelimiter.service.TokenBucketRateLimiter;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class RateLimiterSelector {

    private final Map<String, RateLimiterService> algorithms;
    private final RateLimiterProperties properties;

    public RateLimiterSelector(FixedWindowRateLimiter fixedWindowRateLimiter,
                               TokenBucketRateLimiter tokenBucketRateLimiter,
                               SlidingWindowRateLimiter slidingWindowRateLimiter,
                               RateLimiterProperties properties) {
        this.algorithms = Map.of(
                "FIXED_WINDOW", fixedWindowRateLimiter,
                "TOKEN_BUCKET", tokenBucketRateLimiter,
                "SLIDING_WINDOW", slidingWindowRateLimiter
        );
        this.properties = properties;
    }

    public RateLimiterService getActiveAlgorithm() {
        String configured = properties.algorithm();
        RateLimiterService service = algorithms.get(configured);
        if (service == null) {
            throw new IllegalStateException(
                    "Unknown rate-limiter.algorithm: '" + configured + "'. Valid values: " + algorithms.keySet()
            );
        }
        return service;
    }
}