package com.gateflow.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * Custom Micrometer metrics integration for monitoring distributed rate limiting activity.
 */
@Component
public class RateLimiterMetrics {

    private final MeterRegistry meterRegistry;
    private final ConcurrentMap<String, Counter> allowedCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> deniedCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Timer> latencyTimers = new ConcurrentHashMap<>();

    public RateLimiterMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Records an admitted request.
     */
    public void recordAllowed(String algorithm, String endpoint) {
        String key = algorithm + ":" + endpoint;
        allowedCounters.computeIfAbsent(key, k ->
                Counter.builder("gateflow.ratelimit.requests.allowed")
                        .description("Total number of requests permitted by the rate limiter")
                        .tag("algorithm", algorithm)
                        .tag("endpoint", endpoint)
                        .register(meterRegistry)
        ).increment();
    }

    /**
     * Records a rejected (429) request.
     */
    public void recordDenied(String algorithm, String endpoint) {
        String key = algorithm + ":" + endpoint;
        deniedCounters.computeIfAbsent(key, k ->
                Counter.builder("gateflow.ratelimit.requests.denied")
                        .description("Total number of requests blocked by the rate limiter")
                        .tag("algorithm", algorithm)
                        .tag("endpoint", endpoint)
                        .register(meterRegistry)
        ).increment();
    }

    /**
     * Records the execution latency of Lua scripts in Redis.
     */
    public void recordExecutionTime(String algorithm, long durationNanos) {
        latencyTimers.computeIfAbsent(algorithm, a ->
                Timer.builder("gateflow.ratelimit.execution.time")
                        .description("Latency distribution of rate limiting Lua script executions")
                        .tag("algorithm", a)
                        .publishPercentiles(0.5, 0.9, 0.99)
                        .register(meterRegistry)
        ).record(durationNanos, TimeUnit.NANOSECONDS);
    }
}

