package com.gateflow.controller;

import com.gateflow.config.RateLimiterProperties;
import com.gateflow.exception.GlobalExceptionHandler;
import com.gateflow.model.Algorithm;
import com.gateflow.model.RateLimitResult;
import com.gateflow.service.RateLimiterService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DemoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RedisConnectionFactory redisConnectionFactory;

    @MockBean
    private RateLimiterService rateLimiterService;

    @Test
    @DisplayName("GET /api/public is open and returns 200 OK")
    void testPublicEndpoint() throws Exception {
        mockMvc.perform(get("/api/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("GET /api/limited returns 200 OK when rate limiter permits request")
    void testLimitedEndpointAllowed() throws Exception {
        when(rateLimiterService.tryAcquire(anyString(), anyInt(), anyInt(), anyInt(), anyInt(), any()))
                .thenReturn(RateLimitResult.allowed(99L, "TOKEN_BUCKET"));

        mockMvc.perform(get("/api/limited"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Remaining", "99"))
                .andExpect(header().string("X-RateLimit-Algorithm", "TOKEN_BUCKET"))
                .andExpect(jsonPath("$.status").value("success"));
    }

    @Test
    @DisplayName("GET /api/limited returns 429 Too Many Requests when rate limit is exceeded")
    void testLimitedEndpointExceeded() throws Exception {
        when(rateLimiterService.tryAcquire(anyString(), anyInt(), anyInt(), anyInt(), anyInt(), any()))
                .thenReturn(RateLimitResult.denied(0L, 3500L, "TOKEN_BUCKET"));

        mockMvc.perform(get("/api/limited"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "4"))
                .andExpect(header().string("X-RateLimit-Remaining", "0"))
                .andExpect(header().string("X-RateLimit-Algorithm", "TOKEN_BUCKET"))
                .andExpect(jsonPath("$.error").value("Rate Limit Exceeded"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.retryAfterMs").value(3500));
    }

    @Test
    @DisplayName("GET /api/strict executes fallback handler when rate limit is tripped")
    void testStrictFallbackWhenExceeded() throws Exception {
        when(rateLimiterService.tryAcquire(anyString(), anyInt(), anyInt(), anyInt(), anyInt(), eq(Algorithm.TOKEN_BUCKET)))
                .thenReturn(RateLimitResult.denied(0L, 2000L, "TOKEN_BUCKET"));

        mockMvc.perform(get("/api/strict"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value("degraded"))
                .andExpect(jsonPath("$.message").value("High load detected. Fallback cached response returned."))
                .andExpect(jsonPath("$.retryAfterMs").value(2000));
    }

    @Test
    @DisplayName("POST /api/login sliding window protects against brute force")
    void testLoginSlidingWindow() throws Exception {
        when(rateLimiterService.tryAcquire(anyString(), anyInt(), anyInt(), anyInt(), anyInt(), eq(Algorithm.SLIDING_WINDOW)))
                .thenReturn(RateLimitResult.allowed(2L, "SLIDING_WINDOW"));

        mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Remaining", "2"))
                .andExpect(header().string("X-RateLimit-Algorithm", "SLIDING_WINDOW"))
                .andExpect(jsonPath("$.status").value("authenticated"));
    }
}

