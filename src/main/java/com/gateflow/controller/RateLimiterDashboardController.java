package com.gateflow.controller;

import com.gateflow.config.RateLimiterProperties;
import com.gateflow.service.RateLimiterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Management and diagnostic controller exposing operational metrics,
 * live configurations, and cache key administration.
 */
@RestController
@RequestMapping("/api/rate-limiter")
@RequiredArgsConstructor
public class RateLimiterDashboardController {

    private final RateLimiterService rateLimiterService;
    private final RateLimiterProperties properties;

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(rateLimiterService.getStats());
    }

    @GetMapping("/config")
    public ResponseEntity<RateLimiterProperties> getConfig() {
        return ResponseEntity.ok(properties);
    }

    @DeleteMapping("/reset/{key}")
    public ResponseEntity<Map<String, Object>> resetKey(@PathVariable String key) {
        rateLimiterService.resetKey(key);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("message", "Rate limit state reset successfully for key: " + key);
        return ResponseEntity.ok(response);
    }
}

