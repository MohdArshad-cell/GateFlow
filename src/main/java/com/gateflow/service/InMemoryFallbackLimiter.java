package com.gateflow.service;

import com.gateflow.model.RateLimitResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory sliding window fallback rate limiter.
 * Activated during Redis outages to provide graceful degradation and prevent
 * complete bypass of security controls when fail-open is enabled.
 */
@Slf4j
@Component
public class InMemoryFallbackLimiter {

    private final ConcurrentMap<String, Deque<Long>> localWindows = new ConcurrentHashMap<>();

    /**
     * Evaluates sliding window in local memory when Redis is unavailable.
     */
    public RateLimitResult tryAcquire(String key, int maxRequests, int windowSeconds) {
        long now = System.currentTimeMillis();
        long windowMs = windowSeconds > 0 ? windowSeconds * 1000L : 60000L;
        long windowStart = now - windowMs;

        Deque<Long> timestamps = localWindows.computeIfAbsent(key, k -> new ArrayDeque<>());

        synchronized (timestamps) {
            // Evict expired entries
            while (!timestamps.isEmpty() && timestamps.peekFirst() <= windowStart) {
                timestamps.pollFirst();
            }

            int count = timestamps.size();
            if (count < maxRequests) {
                timestamps.addLast(now);
                long remaining = maxRequests - count - 1;
                log.warn("Redis is DOWN! Gracefully degrading to in-memory rate limiter for key '{}' (allowed, remaining={})", key, remaining);
                return RateLimitResult.allowed(remaining, "IN_MEMORY_FALLBACK");
            } else {
                long oldest = timestamps.peekFirst() != null ? timestamps.peekFirst() : now;
                long retryAfterMs = Math.max(1, (oldest + windowMs) - now);
                log.warn("Redis is DOWN! In-memory fallback rate limiter blocked request for key '{}' (retryAfter={}ms)", key, retryAfterMs);
                return RateLimitResult.denied(0L, retryAfterMs, "IN_MEMORY_FALLBACK");
            }
        }
    }

    /**
     * Clears all local in-memory windows.
     */
    public void clear() {
        localWindows.clear();
    }
}

