package com.sujay.ratelimiter.service;

import com.sujay.ratelimiter.config.RateLimiterProperties;
import com.sujay.ratelimiter.model.RateLimitResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TokenBucketRateLimiterTest {

    private StringRedisTemplate redisTemplate;
    private DefaultRedisScript<List> script;
    private TokenBucketRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        script = mock(DefaultRedisScript.class);

        RateLimiterProperties properties = new RateLimiterProperties(
                "TOKEN_BUCKET",
                new RateLimiterProperties.FixedWindow(5, 60),
                new RateLimiterProperties.TokenBucket(10, 2.0) // capacity = 10
        );

        rateLimiter = new TokenBucketRateLimiter(redisTemplate, script, properties, new SimpleMeterRegistry());
    }

    @Test
    void shouldAllow_whenTokensAvailable() {
        // Lua script returns: { allowed=1, tokensRemaining=4, retryAfterMs=0 }
        when(redisTemplate.execute(any(), anyList(), any(Object[].class)))
                .thenReturn(List.of(1L, 4L, 0L));

        RateLimitResult result = rateLimiter.isAllowed("ip:1.2.3.4");

        assertThat(result.allowed()).isTrue();
        assertThat(result.remaining()).isEqualTo(4);
    }

    @Test
    void shouldDeny_whenBucketEmpty() {
        // Lua script returns: { allowed=0, tokensRemaining=0, retryAfterMs=500 }
        when(redisTemplate.execute(any(), anyList(), any(Object[].class)))
                .thenReturn(List.of(0L, 0L, 500L));

        RateLimitResult result = rateLimiter.isAllowed("ip:1.2.3.4");

        assertThat(result.allowed()).isFalse();
        assertThat(result.remaining()).isEqualTo(0);
    }

    @Test
    void shouldFailOpen_whenRedisThrows() {
        when(redisTemplate.execute(any(), anyList(), any(Object[].class)))
                .thenThrow(new RuntimeException("Connection refused"));

        RateLimitResult result = rateLimiter.isAllowed("ip:1.2.3.4");

        assertThat(result.allowed()).isTrue();
    }
}
