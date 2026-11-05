package com.gateflow.exception;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Centralized exception handler producing RFC-compliant 429 Too Many Requests responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> handleRateLimitExceeded(RateLimitExceededException ex) {
        long retrySeconds = Math.max(1, (ex.getRetryAfterMs() + 999) / 1000);

        HttpHeaders headers = new HttpHeaders();
        headers.add("Retry-After", String.valueOf(retrySeconds));
        headers.add("X-RateLimit-Remaining", String.valueOf(ex.getRemainingTokens()));
        headers.add("X-RateLimit-Algorithm", ex.getAlgorithm());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "Rate Limit Exceeded");
        body.put("status", HttpStatus.TOO_MANY_REQUESTS.value());
        body.put("message", ex.getMessage());
        body.put("retryAfterMs", ex.getRetryAfterMs());
        body.put("remainingTokens", ex.getRemainingTokens());
        body.put("algorithm", ex.getAlgorithm());
        body.put("timestamp", Instant.now().toString());

        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .headers(headers)
                .body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "Internal Server Error");
        body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        body.put("message", ex.getMessage());
        body.put("timestamp", Instant.now().toString());

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(body);
    }
}

