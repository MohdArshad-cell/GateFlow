package com.gateflow.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

/**
 * Utility for generating rate limiter cache keys and resolving client IPs behind reverse proxies.
 */
public final class KeyGenerator {

    private static final String KEY_PREFIX = "gateflow:rl";

    private KeyGenerator() {
        // Utility class
    }

    /**
     * Resolves the real client IP address, safely inspecting reverse proxy headers.
     */
    public static String extractClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }

        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xForwardedFor) && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            // First IP in comma-separated list represents the client
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(xRealIp) && !"unknown".equalsIgnoreCase(xRealIp)) {
            return xRealIp.trim();
        }

        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "127.0.0.1";
    }

    /**
     * Builds a structured Redis key for an endpoint.
     * Pattern: gateflow:rl:{algorithm}:{identifier}:{method}:{endpoint}
     */
    public static String generateKey(String algorithmCode, String identifier, String method, String endpoint) {
        String cleanIdentifier = sanitize(identifier);
        String cleanEndpoint = sanitize(endpoint);
        String cleanMethod = method != null ? method.toUpperCase() : "REQ";

        return String.format("%s:%s:%s:%s:%s", KEY_PREFIX, algorithmCode.toLowerCase(), cleanIdentifier, cleanMethod, cleanEndpoint);
    }

    /**
     * Builds a global rate limiter key for Layer-1 filter.
     */
    public static String generateGlobalKey(String identifier) {
        return String.format("gateflow:global:%s", sanitize(identifier));
    }

    /**
     * Sanitizes inputs to prevent Redis key injection / malformed keys.
     */
    public static String sanitize(String input) {
        if (!StringUtils.hasText(input)) {
            return "default";
        }
        return input.trim().replaceAll("[\\s*]+", "_");
    }
}

