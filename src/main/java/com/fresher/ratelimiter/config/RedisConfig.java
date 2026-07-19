package com.fresher.ratelimiter.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

@Configuration
public class RedisConfig {

    // StringRedisTemplate is Spring's ready-made template for the common case:
    // both keys and values are plain strings. No custom serializer setup needed.
    @Bean
    public StringRedisTemplate redisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }

    // Fixed window doesn't need Lua — Redis's INCR command is already atomic on
    // its own. Token bucket DOES need Lua, because it has to read two values
    // (tokens, last_refill), do math on them, and write them back — and that
    // read-modify-write sequence has to happen as one indivisible step, or two
    // requests arriving at the same instant could both read stale data.
    @Bean
    public DefaultRedisScript<List> tokenBucketScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("scripts/token_bucket.lua"));
        script.setResultType(List.class);
        return script;
    }
}
