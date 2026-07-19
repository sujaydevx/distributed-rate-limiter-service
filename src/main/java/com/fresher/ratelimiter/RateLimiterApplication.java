package com.fresher.ratelimiter;

import com.fresher.ratelimiter.config.RateLimiterProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
// Turns the rate-limiter.* section of application.yml into a typed RateLimiterProperties object
@EnableConfigurationProperties(RateLimiterProperties.class)
public class RateLimiterApplication {

    public static void main(String[] args) {
        SpringApplication.run(RateLimiterApplication.class, args);
    }
}
