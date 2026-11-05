package com.gateflow.model;

/**
 * Supported distributed rate limiting algorithms.
 */
public enum Algorithm {
    /**
     * Inherit the default algorithm configured in application properties.
     */
    DEFAULT,

    /**
     * Token Bucket algorithm: maintains a bucket of tokens that refilled at a constant rate.
     * Ideal for handling burst traffic while enforcing long-term average rates.
     */
    TOKEN_BUCKET,

    /**
     * Sliding Window Log algorithm: maintains precise timestamps of past requests in a Redis Sorted Set.
     * Guarantees strict rate limits across any moving time window without boundary burst loopholes.
     */
    SLIDING_WINDOW
}

