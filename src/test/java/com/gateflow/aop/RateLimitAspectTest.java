package com.gateflow.aop;

import com.gateflow.annotation.RateLimit;
import com.gateflow.config.RateLimiterProperties;
import com.gateflow.exception.RateLimitExceededException;
import com.gateflow.model.Algorithm;
import com.gateflow.model.RateLimitResult;
import com.gateflow.service.RateLimiterService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitAspectTest {

    @Mock
    private RateLimiterService rateLimiterService;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    @Mock
    private RateLimit rateLimit;

    private RateLimiterProperties properties;
    private RateLimitAspect aspect;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        properties = new RateLimiterProperties();
        properties.setEnabled(true);
        aspect = new RateLimitAspect(rateLimiterService, properties);

        request = new MockHttpServletRequest("GET", "/api/test");
        response = new MockHttpServletResponse();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, response));
    }

    @Test
    @DisplayName("AOP: Allowed request attaches rate limit response headers and proceeds")
    void testAllowedRequestAddsHeaders() throws Throwable {
        when(rateLimit.key()).thenReturn("");
        when(rateLimit.algorithm()).thenReturn(Algorithm.DEFAULT);
        when(rateLimit.capacity()).thenReturn(50);
        when(rateLimit.refillRate()).thenReturn(5);

        when(rateLimiterService.tryAcquire(anyString(), anyInt(), anyInt(), anyInt(), anyInt(), any()))
                .thenReturn(RateLimitResult.allowed(49L, "TOKEN_BUCKET"));

        when(joinPoint.proceed()).thenReturn("success-result");

        Object result = aspect.enforceRateLimit(joinPoint, rateLimit);

        assertThat(result).isEqualTo("success-result");
        assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("50");
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("49");
        assertThat(response.getHeader("X-RateLimit-Algorithm")).isEqualTo("TOKEN_BUCKET");
        verify(joinPoint).proceed();
    }

    @Test
    @DisplayName("AOP: Denied request with no fallback throws RateLimitExceededException")
    void testDeniedRequestThrowsException() throws Throwable {
        when(rateLimit.key()).thenReturn("");
        when(rateLimit.algorithm()).thenReturn(Algorithm.TOKEN_BUCKET);
        when(rateLimit.fallbackMethod()).thenReturn("");

        when(rateLimiterService.tryAcquire(anyString(), anyInt(), anyInt(), anyInt(), anyInt(), any()))
                .thenReturn(RateLimitResult.denied(0L, 2500L, "TOKEN_BUCKET"));

        assertThatThrownBy(() -> aspect.enforceRateLimit(joinPoint, rateLimit))
                .isInstanceOf(RateLimitExceededException.class)
                .satisfies(ex -> {
                    RateLimitExceededException rle = (RateLimitExceededException) ex;
                    assertThat(rle.getRetryAfterMs()).isEqualTo(2500L);
                    assertThat(rle.getRemainingTokens()).isZero();
                });

        verify(joinPoint, never()).proceed();
    }

    @Test
    @DisplayName("AOP: Denied request with fallback invokes target fallback method")
    void testDeniedRequestWithFallback() throws Throwable {
        TestController target = new TestController();
        Method method = TestController.class.getMethod("action");

        when(rateLimit.key()).thenReturn("");
        when(rateLimit.algorithm()).thenReturn(Algorithm.TOKEN_BUCKET);
        when(rateLimit.fallbackMethod()).thenReturn("actionFallback");

        when(joinPoint.getTarget()).thenReturn(target);
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);

        when(rateLimiterService.tryAcquire(anyString(), anyInt(), anyInt(), anyInt(), anyInt(), any()))
                .thenReturn(RateLimitResult.denied(0L, 3000L, "TOKEN_BUCKET"));

        Object result = aspect.enforceRateLimit(joinPoint, rateLimit);

        assertThat(result).isEqualTo("fallback-invoked-3000");
        verify(joinPoint, never()).proceed();
    }

    @Test
    @DisplayName("AOP: When rate limiting is disabled, passes through without calling Redis")
    void testDisabledPassesThrough() throws Throwable {
        properties.setEnabled(false);
        when(joinPoint.proceed()).thenReturn("passthrough");

        Object result = aspect.enforceRateLimit(joinPoint, rateLimit);

        assertThat(result).isEqualTo("passthrough");
        verify(rateLimiterService, never()).tryAcquire(any(), anyInt(), anyInt(), anyInt(), anyInt(), any());
    }

    static class TestController {
        public String action() {
            return "original";
        }

        public String actionFallback(RateLimitResult result) {
            return "fallback-invoked-" + result.retryAfterMs();
        }
    }
}

