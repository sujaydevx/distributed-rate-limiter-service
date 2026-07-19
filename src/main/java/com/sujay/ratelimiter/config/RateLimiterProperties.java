package com.sujay.ratelimiter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the "rate-limiter" section of application.yml into a plain Java object.
 * Using a record here instead of a class with @Data/Lombok keeps things fully
 * explicit — every field you see here is exactly what's in the YAML, nothing hidden
 * behind an annotation that generates code you didn't write.
 */
@ConfigurationProperties(prefix = "rate-limiter")
public record RateLimiterProperties(
        String algorithm,
        FixedWindow fixedWindow,
        TokenBucket tokenBucket,
        SlidingWindow slidingWindow
) {
    public record FixedWindow(int maxRequests, int windowSeconds) {}
    public record TokenBucket(int capacity, double refillRate) {}
    public record SlidingWindow(int maxRequests, int windowSeconds) {}
}
