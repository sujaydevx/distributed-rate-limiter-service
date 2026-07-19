package com.fresher.ratelimiter.controller;

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
    public Map<String, String> hello() {
        return Map.of("message", "Hello! Your request was allowed.");
    }
}
