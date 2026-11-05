package com.gateflow.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateflow.config.RateLimiterProperties;
import com.gateflow.model.Algorithm;
import com.gateflow.model.RateLimitResult;
import com.gateflow.service.RateLimiterService;
import com.gateflow.util.KeyGenerator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Layer-1 Global Servlet Filter enforcing whole-system rate limiting per client IP.
 * Stops volumetric DDoS attacks before they reach internal controllers or AOP interceptors.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiterService rateLimiterService;
    private final RateLimiterProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        RateLimiterProperties.GlobalFilterProperties globalConfig = properties.getGlobalFilter();

        if (!properties.isEnabled() || !globalConfig.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = KeyGenerator.extractClientIp(request);
        String globalKey = KeyGenerator.generateGlobalKey(clientIp);

        RateLimitResult result = rateLimiterService.tryAcquire(
                globalKey,
                globalConfig.getCapacity(),
                globalConfig.getRefillRate(),
                globalConfig.getWindowSizeSeconds(),
                globalConfig.getMaxRequests(),
                Algorithm.TOKEN_BUCKET
        );

        if (result.allowed()) {
            response.setHeader("X-Global-RateLimit-Limit", String.valueOf(globalConfig.getCapacity()));
            response.setHeader("X-Global-RateLimit-Remaining", String.valueOf(result.remainingTokens()));
            filterChain.doFilter(request, response);
            return;
        }

        // Global rate limit exceeded (Layer-1 DDoS mitigation)
        log.warn("Global rate limit exceeded for client IP: {}", clientIp);
        long retrySeconds = Math.max(1, (result.retryAfterMs() + 999) / 1000);

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", String.valueOf(retrySeconds));
        response.setHeader("X-Global-RateLimit-Remaining", "0");

        Map<String, Object> errorPayload = new LinkedHashMap<>();
        errorPayload.put("error", "Global Rate Limit Exceeded");
        errorPayload.put("status", HttpStatus.TOO_MANY_REQUESTS.value());
        errorPayload.put("message", "Global IP request limit exceeded. Please back off.");
        errorPayload.put("retryAfterMs", result.retryAfterMs());
        errorPayload.put("timestamp", Instant.now().toString());

        objectMapper.writeValue(response.getWriter(), errorPayload);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator")
                || path.startsWith("/api/rate-limiter")
                || path.equals("/favicon.ico");
    }
}

