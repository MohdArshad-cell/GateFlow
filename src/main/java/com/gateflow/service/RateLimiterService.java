package com.gateflow.service;

import com.gateflow.model.Algorithm;
import com.gateflow.model.RateLimitResult;

import java.util.Map;

/**
 * Service contract for distributed rate limiting.
 */
public interface RateLimiterService {

    /**
     * Attempts to acquire execution quota against the specified rate limit key.
     *
     * @param key               the unique distributed key
     * @param capacity          maximum token capacity (Token Bucket)
     * @param refillRate        tokens per second refill (Token Bucket)
     * @param windowSizeSeconds window size in seconds (Sliding Window)
     * @param maxRequests       maximum requests allowed per window (Sliding Window)
     * @param algorithm         target algorithm strategy
     * @return result indicating if request is allowed or rate limited
     */
    RateLimitResult tryAcquire(
            String key,
            int capacity,
            int refillRate,
            int windowSizeSeconds,
            int maxRequests,
            Algorithm algorithm
    );

    /**
     * Resets / clears all rate limiting records associated with the specified key.
     *
     * @param key target Redis key
     */
    void resetKey(String key);

    /**
     * Retrieves cluster-wide rate limiting statistics and Redis metadata.
     *
     * @return key-value map of runtime stats
     */
    Map<String, Object> getStats();
}

