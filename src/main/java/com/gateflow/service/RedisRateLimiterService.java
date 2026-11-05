package com.gateflow.service;

import com.gateflow.config.RateLimiterProperties;
import com.gateflow.exception.RateLimitExceededException;
import com.gateflow.metrics.RateLimiterMetrics;
import com.gateflow.model.Algorithm;
import com.gateflow.model.RateLimitResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

/**
 * High-performance, distributed rate limiter service backed by Redis and atomic Lua scripts.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisRateLimiterService implements RateLimiterService {

    private final RedisTemplate<String, String> redisTemplate;
    private final RedisScript<List<Long>> tokenBucketScript;
    private final RedisScript<List<Long>> slidingWindowScript;
    private final RateLimiterProperties properties;
    private final RateLimiterMetrics metrics;

    @Override
    public RateLimitResult tryAcquire(
            String key,
            int capacity,
            int refillRate,
            int windowSizeSeconds,
            int maxRequests,
            Algorithm algorithm
    ) {
        if (!properties.isEnabled()) {
            return RateLimitResult.allowed(Long.MAX_VALUE, "DISABLED");
        }

        Algorithm resolvedAlgorithm = (algorithm == null || algorithm == Algorithm.DEFAULT)
                ? properties.getDefaultAlgorithm()
                : algorithm;

        long startTime = System.nanoTime();
        try {
            RateLimitResult result;
            if (resolvedAlgorithm == Algorithm.SLIDING_WINDOW) {
                result = executeSlidingWindow(key, windowSizeSeconds, maxRequests);
            } else {
                result = executeTokenBucket(key, capacity, refillRate);
            }

            long durationNanos = System.nanoTime() - startTime;
            metrics.recordExecutionTime(resolvedAlgorithm.name(), durationNanos);

            if (result.allowed()) {
                metrics.recordAllowed(resolvedAlgorithm.name(), key);
            } else {
                metrics.recordDenied(resolvedAlgorithm.name(), key);
            }

            log.debug("RateLimit check [{}]: key={}, allowed={}, remaining={}, retryAfterMs={}ms",
                    resolvedAlgorithm, key, result.allowed(), result.remainingTokens(), result.retryAfterMs());

            return result;
        } catch (Exception ex) {
            log.error("Redis rate limiter execution failure for key: {}", key, ex);

            if (properties.isFailOpen()) {
                log.warn("Fail-open active: allowing request despite Redis error for key: {}", key);
                return RateLimitResult.allowed(1L, resolvedAlgorithm.name() + "_FAIL_OPEN");
            } else {
                throw new RateLimitExceededException(
                        "Rate limiter unavailable and fail-open is disabled: " + ex.getMessage(),
                        1000L,
                        0L,
                        resolvedAlgorithm.name()
                );
            }
        }
    }

    private RateLimitResult executeTokenBucket(String key, int capacity, int refillRate) {
        int effCapacity = capacity > 0 ? capacity : properties.getDefaultCapacity();
        int effRefillRate = refillRate > 0 ? refillRate : properties.getDefaultRefillRate();
        long now = System.currentTimeMillis();
        int requested = 1;

        List<String> keys = Collections.singletonList(key);
        String[] args = new String[]{
                String.valueOf(effCapacity),
                String.valueOf(effRefillRate),
                String.valueOf(now),
                String.valueOf(requested)
        };

        List<Long> response = redisTemplate.execute(tokenBucketScript, keys, (Object[]) args);
        return parseLuaResponse(response, Algorithm.TOKEN_BUCKET.name());
    }

    private RateLimitResult executeSlidingWindow(String key, int windowSizeSeconds, int maxRequests) {
        int effWindowSec = windowSizeSeconds > 0 ? windowSizeSeconds : properties.getDefaultWindowSizeSeconds();
        int effMaxRequests = maxRequests > 0 ? maxRequests : properties.getDefaultMaxRequests();
        long windowMs = effWindowSec * 1000L;
        long now = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString();

        List<String> keys = Collections.singletonList(key);
        String[] args = new String[]{
                String.valueOf(windowMs),
                String.valueOf(effMaxRequests),
                String.valueOf(now),
                requestId
        };

        List<Long> response = redisTemplate.execute(slidingWindowScript, keys, (Object[]) args);
        return parseLuaResponse(response, Algorithm.SLIDING_WINDOW.name());
    }

    private RateLimitResult parseLuaResponse(List<Long> response, String algorithm) {
        if (response == null || response.size() < 3) {
            log.warn("Unexpected null or partial response from Lua script for {}", algorithm);
            return RateLimitResult.denied(0L, 1000L, algorithm);
        }

        boolean allowed = response.get(0) != null && response.get(0) == 1L;
        long remaining = response.get(1) != null ? response.get(1) : 0L;
        long retryAfterMs = response.get(2) != null ? response.get(2) : 0L;

        return allowed
                ? RateLimitResult.allowed(remaining, algorithm)
                : RateLimitResult.denied(remaining, retryAfterMs, algorithm);
    }

    @Override
    public void resetKey(String key) {
        Boolean deleted = redisTemplate.delete(key);
        log.info("Reset rate limit state for key: {} (deleted={})", key, deleted);
    }

    @Override
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("service", "GateFlow");
        stats.put("timestamp", Instant.now().toString());
        stats.put("enabled", properties.isEnabled());
        stats.put("failOpen", properties.isFailOpen());
        stats.put("defaultAlgorithm", properties.getDefaultAlgorithm());

        try {
            Properties info = redisTemplate.execute((RedisCallback<Properties>) RedisConnection::info);
            if (info != null) {
                stats.put("redisVersion", info.getProperty("redis_version", "unknown"));
                stats.put("connectedClients", info.getProperty("connected_clients", "unknown"));
                stats.put("usedMemoryHuman", info.getProperty("used_memory_human", "unknown"));
                stats.put("uptimeInSeconds", info.getProperty("uptime_in_seconds", "unknown"));
            }

            Set<String> keys = redisTemplate.keys("gateflow:*");
            stats.put("activeRateLimitKeys", keys != null ? keys.size() : 0);
        } catch (Exception e) {
            stats.put("redisError", e.getMessage());
        }

        return stats;
    }
}

