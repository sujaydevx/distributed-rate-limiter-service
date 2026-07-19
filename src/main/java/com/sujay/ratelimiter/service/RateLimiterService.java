package com.sujay.ratelimiter.service;

import com.sujay.ratelimiter.model.RateLimitResult;

/**
 * Anything that can answer one question: "should this client's request be allowed?"
 * Having this as an interface means the rest of the app (the filter) doesn't care
 * which algorithm is actually running underneath.
 */
public interface RateLimiterService {
    RateLimitResult isAllowed(String clientId);
}
