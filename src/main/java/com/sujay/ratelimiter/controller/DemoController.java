package com.sujay.ratelimiter.controller;

import com.sujay.ratelimiter.filter.RateLimitFilter;
import com.sujay.ratelimiter.model.HelloResponse;
import com.sujay.ratelimiter.model.RateLimitInfo;
import com.sujay.ratelimiter.model.RateLimitResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * A stand-in for "the real API" this project protects. It knows nothing about
 * rate limiting — the RateLimitFilter handles that before the request ever
 * reaches here. That separation (business logic vs. cross-cutting infra
 * concern) is the point.
 */
@RestController
@RequestMapping("/api")
public class DemoController {

    @GetMapping("/hello")
    public ResponseEntity<HelloResponse> hello(HttpServletRequest request) {
        RateLimitResult rateLimitResult =
                (RateLimitResult) request.getAttribute(RateLimitFilter.RATE_LIMIT_RESULT_ATTRIBUTE);

        HelloResponse body = new HelloResponse(
                "Hello! Your request was allowed.",
                RateLimitInfo.from(rateLimitResult)
        );

        return ResponseEntity.ok(body);
    }
}
