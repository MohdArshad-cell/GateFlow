package com.gateflow.model;

/**
 * Result of a rate limit evaluation.
 *
 * @param allowed         true if the request was admitted, false if rejected (429)
 * @param remainingTokens available quota remaining in the current window or bucket
 * @param retryAfterMs    suggested backoff time in milliseconds before the next retry is allowed
 * @param algorithm       the algorithm used for this evaluation
 */
public record RateLimitResult(
        boolean allowed,
        long remainingTokens,
        long retryAfterMs,
        String algorithm
) {
    public static RateLimitResult allowed(long remainingTokens, String algorithm) {
        return new RateLimitResult(true, remainingTokens, 0L, algorithm);
    }

    public static RateLimitResult denied(long remainingTokens, long retryAfterMs, String algorithm) {
        return new RateLimitResult(false, remainingTokens, retryAfterMs, algorithm);
    }
}

