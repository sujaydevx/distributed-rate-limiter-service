package com.fresher.ratelimiter.model;

/**
 * The outcome of one rate-limit check.
 *
 * @param allowed          true if the request should proceed
 * @param remaining        how many requests this client has left in the current window/bucket
 * @param retryAfterSeconds how long the client should wait before trying again (0 if allowed)
 */
public record RateLimitResult(boolean allowed, long remaining, long retryAfterSeconds) {
}
