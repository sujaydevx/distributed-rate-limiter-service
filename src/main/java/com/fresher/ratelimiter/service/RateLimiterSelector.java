package com.fresher.ratelimiter.service;

import com.fresher.ratelimiter.config.RateLimiterProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Picks which of the two algorithms is "active" based on application.yml's
 * rate-limiter.algorithm setting. Deliberately just a two-entry Map, not a
 * dynamically-built factory — with only two algorithms, a map you can read at
 * a glance is clearer than a mechanism that builds itself.
 */
@Component
public class RateLimiterSelector {

    private final Map<String, RateLimiterService> algorithms;
    private final RateLimiterProperties properties;

    public RateLimiterSelector(FixedWindowRateLimiter fixedWindowRateLimiter,
                                TokenBucketRateLimiter tokenBucketRateLimiter,
                                RateLimiterProperties properties) {
        this.algorithms = Map.of(
                "FIXED_WINDOW", fixedWindowRateLimiter,
                "TOKEN_BUCKET", tokenBucketRateLimiter
        );
        this.properties = properties;
    }

    public RateLimiterService getActiveAlgorithm() {
        String configured = properties.algorithm();
        RateLimiterService service = algorithms.get(configured);
        if (service == null) {
            throw new IllegalStateException(
                    "Unknown rate-limiter.algorithm: '" + configured +
                            "'. Valid values: " + algorithms.keySet()
            );
        }
        return service;
    }
}
