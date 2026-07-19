package com.sujay.ratelimiter.model;

public record RateLimitInfo(
        long limit,
        long remaining,
        long retryAfterSeconds,
        String algorithm
) {
    public static RateLimitInfo from(RateLimitResult result) {
        return new RateLimitInfo(result.limit(), result.remaining(), result.retryAfterSeconds(), result.algorithm());
    }
}