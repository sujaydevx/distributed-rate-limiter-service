package com.sujay.ratelimiter.model;


import java.time.Instant;

public record RateLimitErrorResponse(
        String error,
        String message,
        RateLimitInfo rateLimit,
        Instant timestamp
) {
    public static RateLimitErrorResponse from(RateLimitResult result) {
        return new RateLimitErrorResponse(
                "TOO_MANY_REQUESTS",
                "Rate limit exceeded. Try again in " + result.retryAfterSeconds() + " second(s).",
                RateLimitInfo.from(result),
                Instant.now()
        );
    }
}