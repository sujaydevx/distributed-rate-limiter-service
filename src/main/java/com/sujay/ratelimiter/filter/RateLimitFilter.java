package com.sujay.ratelimiter.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sujay.ratelimiter.model.RateLimitResult;
import com.sujay.ratelimiter.service.RateLimiterSelector;
import com.sujay.ratelimiter.model.RateLimitErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;


import java.io.IOException;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    public static final String RATE_LIMIT_RESULT_ATTRIBUTE = "rateLimitResult";

    private final RateLimiterSelector rateLimiterSelector;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(RateLimiterSelector rateLimiterSelector, ObjectMapper objectMapper) {
        this.rateLimiterSelector = rateLimiterSelector;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (request.getRequestURI().startsWith("/actuator")) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientId = resolveClientId(request);
        RateLimitResult result = rateLimiterSelector.getActiveAlgorithm().isAllowed(clientId);

        response.setHeader("X-RateLimit-Limit", String.valueOf(result.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remaining()));
        response.setHeader("X-RateLimit-Algorithm", result.algorithm());

        if (result.allowed()) {
            request.setAttribute(RATE_LIMIT_RESULT_ATTRIBUTE, result);
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(result.retryAfterSeconds()));
            response.setContentType("application/json");

            RateLimitErrorResponse errorBody = RateLimitErrorResponse.from(result);
            response.getWriter().write(objectMapper.writeValueAsString(errorBody));
        }
    }

    private String resolveClientId(HttpServletRequest request) {
        String testOverride = request.getHeader("X-Client-Id");
        if (testOverride != null && !testOverride.isBlank()) {
            return "client:" + testOverride;
        }

        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return "ip:" + forwarded.split(",")[0].trim();
        }

        return "ip:" + request.getRemoteAddr();
    }
}