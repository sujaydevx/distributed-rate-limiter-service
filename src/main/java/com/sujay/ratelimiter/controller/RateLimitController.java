package com.sujay.ratelimiter.controller;

import com.sujay.ratelimiter.model.RateLimitCheckResponse;
import com.sujay.ratelimiter.model.RateLimitResult;
import com.sujay.ratelimiter.service.RateLimiterSelector;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ratelimit")
public class RateLimitController {

    private final RateLimiterSelector rateLimiterSelector;

    public RateLimitController(RateLimiterSelector rateLimiterSelector) {
        this.rateLimiterSelector = rateLimiterSelector;
    }

    @GetMapping("/check")
    public ResponseEntity<RateLimitCheckResponse> check(@RequestParam String clientId) {
        RateLimitResult result = rateLimiterSelector.getActiveAlgorithm().isAllowed("client:" + clientId);
        RateLimitCheckResponse body = RateLimitCheckResponse.from(clientId, result);

        return result.allowed()
                ? ResponseEntity.ok(body)
                : ResponseEntity.status(429).body(body);
    }
}