package com.gateflow.controller;

import com.gateflow.annotation.DistributedRateLimit;
import com.gateflow.annotation.RateLimit;
import com.gateflow.model.Algorithm;
import com.gateflow.model.RateLimitResult;
import com.gateflow.service.TierService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Demo API endpoints showcasing various rate limiting patterns and algorithm strategies.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DemoController {

    private final TierService tierService;

    /**
     * Public endpoint with no rate limit applied (subject only to global filter).
     */
    @GetMapping("/public")
    public ResponseEntity<Map<String, Object>> publicEndpoint() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("message", "This is a public endpoint with no route-level rate limits.");
        response.put("timestamp", Instant.now().toString());
        return ResponseEntity.ok(response);
    }

    /**
     * Endpoint governed by default rate limit configuration (Token Bucket).
     */
    @RateLimit
    @GetMapping("/limited")
    public ResponseEntity<Map<String, Object>> limitedEndpoint() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("message", "Request succeeded within standard rate limits.");
        response.put("timestamp", Instant.now().toString());
        return ResponseEntity.ok(response);
    }

    /**
     * Strict endpoint: burst capacity of 5, refilling at 1 token per second.
     * Includes an automatic fallback handler.
     */
    @RateLimit(
            capacity = 5,
            refillRate = 1,
            algorithm = Algorithm.TOKEN_BUCKET,
            fallbackMethod = "strictFallback"
    )
    @GetMapping("/strict")
    public ResponseEntity<Map<String, Object>> strictEndpoint() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("message", "High-security operation executed within strict quota.");
        response.put("timestamp", Instant.now().toString());
        return ResponseEntity.ok(response);
    }

    /**
     * Fallback method executed when strictEndpoint trips the rate limiter.
     */
    public ResponseEntity<Map<String, Object>> strictFallback(RateLimitResult result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "degraded");
        response.put("message", "High load detected. Fallback cached response returned.");
        response.put("retryAfterMs", result.retryAfterMs());
        response.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(429).body(response);
    }

    /**
     * Endpoint demonstrating @DistributedRateLimit with SpEL key evaluation:
     * Rate limits per specific userId parameter dynamically across the sliding window.
     */
    @DistributedRateLimit(key = "#userId", limit = 10, windowInSeconds = 60)
    @GetMapping("/users/{userId}/orders")
    public ResponseEntity<Map<String, Object>> getUserOrders(@PathVariable("userId") String userId) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("userId", userId);
        response.put("orders", List.of("ORD-101", "ORD-102"));
        response.put("timestamp", Instant.now().toString());
        return ResponseEntity.ok(response);
    }

    /**
     * Dynamic Tiering endpoint:
     * Inspects subscription tier (FREE, PRO, ENTERPRISE) from API key.
     */
    @GetMapping("/tier/info")
    public ResponseEntity<Map<String, Object>> getTierInfo(@RequestHeader(value = "X-API-KEY", required = false) String apiKey) {
        TierService.Tier tier = tierService.resolveTier(apiKey);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("apiKey", apiKey != null ? apiKey : "anonymous");
        response.put("tier", tier.name());
        response.put("limit", tier.getLimit());
        response.put("windowSeconds", tier.getWindowSeconds());
        return ResponseEntity.ok(response);
    }

    /**
     * Sensitive login endpoint governed by Sliding Window Log algorithm.
     * Prevents brute-force credential stuffing: maximum 3 login attempts per 60 seconds per client.
     */
    @RateLimit(
            key = "login",
            windowSizeSeconds = 60,
            maxRequests = 3,
            algorithm = Algorithm.SLIDING_WINDOW
    )
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> loginEndpoint(@RequestBody(required = false) Map<String, String> credentials) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "authenticated");
        response.put("message", "Login attempt processed successfully.");
        response.put("timestamp", Instant.now().toString());
        return ResponseEntity.ok(response);
    }

    /**
     * Health check endpoint verifying service status.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "UP");
        response.put("service", "GateFlow");
        response.put("timestamp", Instant.now().toString());
        return ResponseEntity.ok(response);
    }
}

