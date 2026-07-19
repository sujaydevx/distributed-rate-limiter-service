package com.sujay.ratelimiter.model;

import java.time.Instant;

public record RateLimitCheckResponse(
        boolean allowed,
        String clientId,
        RateLimitInfo rateLimit,
        Instant checkedAt
) {
    public static RateLimitCheckResponse from(String clientId, RateLimitResult result) {
        return new RateLimitCheckResponse(result.allowed(), clientId, RateLimitInfo.from(result), Instant.now());
    }
}