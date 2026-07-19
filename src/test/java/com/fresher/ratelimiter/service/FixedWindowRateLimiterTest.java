package com.fresher.ratelimiter.service;

import com.fresher.ratelimiter.config.RateLimiterProperties;
import com.fresher.ratelimiter.model.RateLimitResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class FixedWindowRateLimiterTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private FixedWindowRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        RateLimiterProperties properties = new RateLimiterProperties(
                "FIXED_WINDOW",
                new RateLimiterProperties.FixedWindow(5, 60), // limit = 5 per 60s
                new RateLimiterProperties.TokenBucket(10, 2.0)
        );

        rateLimiter = new FixedWindowRateLimiter(redisTemplate, properties, new SimpleMeterRegistry());
    }

    @Test
    void shouldAllow_whenUnderLimit() {
        // Simulate this being the 3rd request in the window (limit is 5)
        when(valueOperations.increment(anyString())).thenReturn(3L);
        when(redisTemplate.getExpire(anyString())).thenReturn(45L);

        RateLimitResult result = rateLimiter.isAllowed("ip:1.2.3.4");

        assertThat(result.allowed()).isTrue();
        assertThat(result.remaining()).isEqualTo(2); // 5 - 3
    }

    @Test
    void shouldDeny_whenOverLimit() {
        // 6th request, limit is 5
        when(valueOperations.increment(anyString())).thenReturn(6L);
        when(redisTemplate.getExpire(anyString())).thenReturn(20L);

        RateLimitResult result = rateLimiter.isAllowed("ip:1.2.3.4");

        assertThat(result.allowed()).isFalse();
        assertThat(result.remaining()).isEqualTo(0);
        assertThat(result.retryAfterSeconds()).isEqualTo(20);
    }

    @Test
    void shouldSetExpiry_onlyOnFirstRequestInWindow() {
        // 1st request in a fresh window
        when(valueOperations.increment(anyString())).thenReturn(1L);
        when(redisTemplate.getExpire(anyString())).thenReturn(60L);

        rateLimiter.isAllowed("ip:1.2.3.4");

        verify(redisTemplate, times(1)).expire(anyString(), any());
    }

    @Test
    void shouldFailOpen_whenRedisThrows() {
        when(valueOperations.increment(anyString())).thenThrow(new RuntimeException("Connection refused"));

        RateLimitResult result = rateLimiter.isAllowed("ip:1.2.3.4");

        // Redis is down — we choose availability over strict enforcement
        assertThat(result.allowed()).isTrue();
    }
}
