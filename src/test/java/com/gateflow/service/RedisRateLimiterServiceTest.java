package com.gateflow.service;

import com.gateflow.config.RateLimiterProperties;
import com.gateflow.exception.RateLimitExceededException;
import com.gateflow.metrics.RateLimiterMetrics;
import com.gateflow.model.Algorithm;
import com.gateflow.model.RateLimitResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisRateLimiterServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private RedisScript<List<Long>> tokenBucketScript;

    @Mock
    private RedisScript<List<Long>> slidingWindowScript;

    @Mock
    private RateLimiterMetrics metrics;

    private RateLimiterProperties properties;
    private RedisRateLimiterService service;

    @BeforeEach
    void setUp() {
        properties = new RateLimiterProperties();
        properties.setEnabled(true);
        properties.setFailOpen(false);
        properties.setDefaultAlgorithm(Algorithm.TOKEN_BUCKET);
        properties.setDefaultCapacity(10);
        properties.setDefaultRefillRate(2);
        properties.setDefaultWindowSizeSeconds(60);
        properties.setDefaultMaxRequests(10);

        InMemoryFallbackLimiter inMemoryFallbackLimiter = new InMemoryFallbackLimiter();
        service = new RedisRateLimiterService(
                redisTemplate,
                tokenBucketScript,
                slidingWindowScript,
                properties,
                metrics,
                inMemoryFallbackLimiter
        );
    }

    @Test
    @DisplayName("Token Bucket: Request allowed when tokens available in Redis")
    void testTokenBucketAllowed() {
        when(redisTemplate.execute(eq(tokenBucketScript), any(), any(Object[].class)))
                .thenReturn(List.of(1L, 9L, 0L));

        RateLimitResult result = service.tryAcquire(
                "test-key", 10, 2, 0, 0, Algorithm.TOKEN_BUCKET
        );

        assertThat(result.allowed()).isTrue();
        assertThat(result.remainingTokens()).isEqualTo(9L);
        assertThat(result.retryAfterMs()).isZero();
        verify(metrics).recordAllowed("TOKEN_BUCKET", "test-key");
    }

    @Test
    @DisplayName("Token Bucket: Request rejected (429) when bucket exhausted")
    void testTokenBucketDenied() {
        when(redisTemplate.execute(eq(tokenBucketScript), any(), any(Object[].class)))
                .thenReturn(List.of(0L, 0L, 500L));

        RateLimitResult result = service.tryAcquire(
                "test-key", 10, 2, 0, 0, Algorithm.TOKEN_BUCKET
        );

        assertThat(result.allowed()).isFalse();
        assertThat(result.remainingTokens()).isZero();
        assertThat(result.retryAfterMs()).isEqualTo(500L);
        verify(metrics).recordDenied("TOKEN_BUCKET", "test-key");
    }

    @Test
    @DisplayName("Sliding Window: Request allowed when window count below threshold")
    void testSlidingWindowAllowed() {
        when(redisTemplate.execute(eq(slidingWindowScript), any(), any(Object[].class)))
                .thenReturn(List.of(1L, 4L, 0L));

        RateLimitResult result = service.tryAcquire(
                "sw-key", 0, 0, 60, 5, Algorithm.SLIDING_WINDOW
        );

        assertThat(result.allowed()).isTrue();
        assertThat(result.remainingTokens()).isEqualTo(4L);
        assertThat(result.retryAfterMs()).isZero();
        verify(metrics).recordAllowed("SLIDING_WINDOW", "sw-key");
    }

    @Test
    @DisplayName("Fail-close policy: Throws RateLimitExceededException when Redis is down")
    void testFailCloseOnRedisError() {
        properties.setFailOpen(false);
        when(redisTemplate.execute(any(RedisScript.class), any(), any(Object[].class)))
                .thenThrow(new RuntimeException("Redis connection refused"));

        assertThatThrownBy(() -> service.tryAcquire("err-key", 10, 2, 0, 0, Algorithm.TOKEN_BUCKET))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("Rate limiter unavailable and fail-open is disabled");
    }

    @Test
    @DisplayName("Fail-open policy: Gracefully degrades to in-memory fallback when Redis is down")
    void testFailOpenOnRedisError() {
        properties.setFailOpen(true);
        when(redisTemplate.execute(any(RedisScript.class), any(), any(Object[].class)))
                .thenThrow(new RuntimeException("Redis connection timed out"));

        RateLimitResult result = service.tryAcquire("err-key", 10, 2, 0, 0, Algorithm.TOKEN_BUCKET);

        assertThat(result.allowed()).isTrue();
        assertThat(result.algorithm()).isEqualTo("IN_MEMORY_FALLBACK");
    }

    @Test
    @DisplayName("Bypass: Returns allowed immediately when rate limiter is globally disabled")
    void testDisabledBypass() {
        properties.setEnabled(false);

        RateLimitResult result = service.tryAcquire("any-key", 10, 2, 0, 0, Algorithm.TOKEN_BUCKET);

        assertThat(result.allowed()).isTrue();
        assertThat(result.algorithm()).isEqualTo("DISABLED");
    }

    @Test
    @DisplayName("Reset Key: Invokes Redis DELETE operation")
    void testResetKey() {
        service.resetKey("gateflow:rl:test");
        verify(redisTemplate).delete("gateflow:rl:test");
    }

    @Test
    @DisplayName("Concurrent requests simulation (50 threads) proves thread safety")
    void testConcurrentExecutions() throws InterruptedException {
        int threads = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        AtomicInteger totalAllowed = new AtomicInteger();
        AtomicInteger totalDenied = new AtomicInteger();

        // Simulate atomic Redis responses (first 10 allowed, subsequent denied)
        AtomicInteger counter = new AtomicInteger(10);
        when(redisTemplate.execute(eq(tokenBucketScript), any(), any(Object[].class)))
                .thenAnswer(invocation -> {
                    int remaining = counter.decrementAndGet();
                    if (remaining >= 0) {
                        return List.of(1L, (long) remaining, 0L);
                    } else {
                        return List.of(0L, 0L, 1000L);
                    }
                });

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    RateLimitResult res = service.tryAcquire("concurrent-key", 10, 2, 0, 0, Algorithm.TOKEN_BUCKET);
                    if (res.allowed()) {
                        totalAllowed.incrementAndGet();
                    } else {
                        totalDenied.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertThat(totalAllowed.get()).isEqualTo(10);
        assertThat(totalDenied.get()).isEqualTo(40);
    }
}
