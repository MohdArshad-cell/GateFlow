package com.gateflow.config;

import com.gateflow.model.Algorithm;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for GateFlow rate limiting middleware.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "rate-limiter")
public class RateLimiterProperties {

    /**
     * Master toggle for rate limiting. When false, all requests bypass checks.
     */
    private boolean enabled = true;

    /**
     * Fail-open policy.
     * When true: If Redis is unavailable, requests are permitted (prioritize availability).
     * When false: If Redis is unavailable, requests are rejected with 429 (prioritize security).
     */
    private boolean failOpen = false;

    /**
     * Default rate limiting algorithm (TOKEN_BUCKET or SLIDING_WINDOW).
     */
    private Algorithm defaultAlgorithm = Algorithm.TOKEN_BUCKET;

    /**
     * Default maximum capacity for Token Bucket.
     */
    private int defaultCapacity = 100;

    /**
     * Default refill rate (tokens/sec) for Token Bucket.
     */
    private int defaultRefillRate = 10;

    /**
     * Default sliding window size in seconds for Sliding Window Log.
     */
    private int defaultWindowSizeSeconds = 60;

    /**
     * Default maximum requests permitted in sliding window.
     */
    private int defaultMaxRequests = 100;

    /**
     * Layer-1 global filter settings.
     */
    private GlobalFilterProperties globalFilter = new GlobalFilterProperties();

    @Data
    public static class GlobalFilterProperties {
        private boolean enabled = true;
        private int capacity = 1000;
        private int refillRate = 100;
        private int windowSizeSeconds = 60;
        private int maxRequests = 1000;
    }
}

