package com.sujay.ratelimiter.model;

public record HelloResponse(String message, RateLimitInfo rateLimit) {
}