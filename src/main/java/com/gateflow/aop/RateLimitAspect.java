package com.gateflow.aop;

import com.gateflow.annotation.RateLimit;
import com.gateflow.config.RateLimiterProperties;
import com.gateflow.exception.RateLimitExceededException;
import com.gateflow.model.Algorithm;
import com.gateflow.model.RateLimitResult;
import com.gateflow.service.RateLimiterService;
import com.gateflow.util.KeyGenerator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

/**
 * Aspect-Oriented Programming (AOP) interceptor that inspects methods annotated with @RateLimit.
 * Enforces atomic Redis rate checks with sub-2ms overhead.
 */
@Slf4j
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class RateLimitAspect {

    private final RateLimiterService rateLimiterService;
    private final RateLimiterProperties properties;

    @Around("@annotation(rateLimit)")
    public Object enforceRateLimit(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        if (!properties.isEnabled()) {
            return joinPoint.proceed();
        }

        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes != null ? attributes.getRequest() : null;
        HttpServletResponse response = attributes != null ? attributes.getResponse() : null;

        Algorithm algorithm = (rateLimit.algorithm() == Algorithm.DEFAULT)
                ? properties.getDefaultAlgorithm()
                : rateLimit.algorithm();

        String clientIp = KeyGenerator.extractClientIp(request);
        String identifier = StringUtils.hasText(rateLimit.key()) ? rateLimit.key() : clientIp;
        String method = request != null ? request.getMethod() : "EXEC";
        String endpoint = request != null ? request.getRequestURI() : joinPoint.getSignature().toShortString();

        String redisKey = KeyGenerator.generateKey(algorithm.name(), identifier, method, endpoint);

        int capacity = rateLimit.capacity();
        int refillRate = rateLimit.refillRate();
        int windowSizeSeconds = rateLimit.windowSizeSeconds();
        int maxRequests = rateLimit.maxRequests();

        RateLimitResult result = rateLimiterService.tryAcquire(
                redisKey,
                capacity,
                refillRate,
                windowSizeSeconds,
                maxRequests,
                algorithm
        );

        if (result.allowed()) {
            if (response != null) {
                int limit = (algorithm == Algorithm.SLIDING_WINDOW)
                        ? (maxRequests > 0 ? maxRequests : properties.getDefaultMaxRequests())
                        : (capacity > 0 ? capacity : properties.getDefaultCapacity());
                response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
                response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remainingTokens()));
                response.setHeader("X-RateLimit-Algorithm", result.algorithm());
            }
            return joinPoint.proceed();
        }

        // Rate limit exceeded
        if (StringUtils.hasText(rateLimit.fallbackMethod())) {
            return invokeFallbackMethod(joinPoint, rateLimit.fallbackMethod(), result);
        }

        throw new RateLimitExceededException(
                result.retryAfterMs(),
                result.remainingTokens(),
                result.algorithm()
        );
    }

    private Object invokeFallbackMethod(ProceedingJoinPoint joinPoint, String fallbackMethodName, RateLimitResult result) throws Throwable {
        Object target = joinPoint.getTarget();
        Class<?> targetClass = target.getClass();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        log.warn("Rate limit tripped on {}. Invoking fallback: {}", method.getName(), fallbackMethodName);

        // Try fallback method with RateLimitResult parameter
        try {
            Method fallback = targetClass.getDeclaredMethod(fallbackMethodName, RateLimitResult.class);
            fallback.setAccessible(true);
            return fallback.invoke(target, result);
        } catch (NoSuchMethodException ignored) {
            // Fallback without arguments
            try {
                Method fallback = targetClass.getDeclaredMethod(fallbackMethodName);
                fallback.setAccessible(true);
                return fallback.invoke(target);
            } catch (NoSuchMethodException ex) {
                log.error("Fallback method {} not found on {}", fallbackMethodName, targetClass.getName());
                throw new RateLimitExceededException(result.retryAfterMs(), result.remainingTokens(), result.algorithm());
            }
        }
    }
}

