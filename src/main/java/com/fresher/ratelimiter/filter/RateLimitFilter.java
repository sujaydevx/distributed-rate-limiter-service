package com.fresher.ratelimiter.filter;

import com.fresher.ratelimiter.model.RateLimitResult;
import com.fresher.ratelimiter.service.RateLimiterSelector;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Runs before every controller method. A plain servlet Filter is used here
 * instead of Spring AOP — same end result (intercept the request, decide
 * whether it proceeds), but a Filter is a standard, widely-taught Spring MVC
 * concept, not a proxy mechanism that needs its own explanation.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiterSelector rateLimiterSelector;

    public RateLimitFilter(RateLimiterSelector rateLimiterSelector) {
        this.rateLimiterSelector = rateLimiterSelector;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {

        // Don't rate-limit our own monitoring endpoints, or Prometheus scraping
        // this service every few seconds would itself get rate-limited.
        if (request.getRequestURI().startsWith("/actuator")) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientId = resolveClientId(request);
        RateLimitResult result = rateLimiterSelector.getActiveAlgorithm().isAllowed(clientId);

        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remaining()));

        if (result.allowed()) {
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(429); // HTTP 429 Too Many Requests
            response.setHeader("Retry-After", String.valueOf(result.retryAfterSeconds()));
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"error\":\"Too many requests\",\"retryAfterSeconds\":" + result.retryAfterSeconds() + "}"
            );
        }
    }

    /**
     * How we know "who" is making the request, WITHOUT any login/auth system:
     *
     * Rate limiting only needs an identifier, not proof of identity — it just
     * needs something consistent to count requests against. We use the client's
     * IP address for that. This is exactly what real public APIs (e.g. GitHub's
     * unauthenticated endpoints) do before any user has logged in.
     *
     * Known, honest limitation: multiple real users behind the same IP (office
     * network, campus Wi-Fi, VPN) will share one bucket. Fixing that properly
     * means rate-limiting by authenticated user ID once a login system exists —
     * that's future scope, not built here.
     *
     * For easy manual testing (e.g. with Postman or k6, simulating "different
     * users" without needing different machines), an optional X-Client-Id header
     * overrides the IP if present. This is a TESTING CONVENIENCE ONLY — it is
     * not authentication, since anyone can put anything in that header.
     */
    private String resolveClientId(HttpServletRequest request) {
        String testOverride = request.getHeader("X-Client-Id");
        if (testOverride != null && !testOverride.isBlank()) {
            return "client:" + testOverride;
        }

        // X-Forwarded-For handles the case where this app sits behind a load
        // balancer or reverse proxy — without this, every request would look
        // like it came from the load balancer's IP, and all real users would
        // share one rate limit.
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return "ip:" + forwarded.split(",")[0].trim();
        }

        return "ip:" + request.getRemoteAddr();
    }
}
