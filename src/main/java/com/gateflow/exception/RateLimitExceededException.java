package com.gateflow.exception;

import lombok.Getter;

/**
 * Exception thrown when a request exceeds the configured rate limit quota.
 */
@Getter
public class RateLimitExceededException extends RuntimeException {

    private final long retryAfterMs;
    private final long remainingTokens;
    private final String algorithm;

    public RateLimitExceededException(String message, long retryAfterMs, long remainingTokens, String algorithm) {
        super(message);
        this.retryAfterMs = retryAfterMs;
        this.remainingTokens = remainingTokens;
        this.algorithm = algorithm;
    }

    public RateLimitExceededException(long retryAfterMs, long remainingTokens, String algorithm) {
        this("Rate limit exceeded. Try again in " + retryAfterMs + "ms.", retryAfterMs, remainingTokens, algorithm);
    }
}

