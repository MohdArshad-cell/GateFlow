package com.gateflow.aop;

import com.gateflow.annotation.DistributedRateLimit;
import com.gateflow.annotation.RateLimit;
import com.gateflow.config.RateLimiterProperties;
import com.gateflow.exception.RateLimitExceededException;
import com.gateflow.model.Algorithm;
import com.gateflow.model.RateLimitResult;
import com.gateflow.service.RateLimiterService;
import com.gateflow.util.KeyGenerator;
import com.gateflow.util.SpelEvaluator;
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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Aspect-Oriented Programming (AOP) interceptor that inspects methods annotated
 * with
 * either @RateLimit or @DistributedRateLimit. Supports Spring Expression
 * Language (SpEL)
 * parameter extraction (e.g. key = "#userId") and attaches RFC standard rate
 * limit headers.
 */
@Slf4j
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class RateLimitAspect {

    private final RateLimiterService rateLimiterService;
    private final RateLimiterProperties properties;

    @Around(value = "@annotation(rateLimit)", argNames = "joinPoint,rateLimit")
    public Object enforceRateLimit(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String evaluatedKey = SpelEvaluator.evaluate(rateLimit.key(), signature.getMethod(), joinPoint.getArgs());

        return doEnforce(
                joinPoint,
                evaluatedKey,
                rateLimit.capacity(),
                rateLimit.refillRate(),
                rateLimit.windowSizeSeconds(),
                rateLimit.maxRequests(),
                rateLimit.algorithm(),
                rateLimit.fallbackMethod());
    }

    @Around(value = "@annotation(distributedRateLimit)", argNames = "joinPoint,distributedRateLimit")
    public Object enforceDistributedRateLimit(ProceedingJoinPoint joinPoint, DistributedRateLimit distributedRateLimit)
            throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String evaluatedKey = SpelEvaluator.evaluate(distributedRateLimit.key(), signature.getMethod(),
                joinPoint.getArgs());

        return doEnforce(
                joinPoint,
                evaluatedKey,
                distributedRateLimit.limit(),
                0,
                distributedRateLimit.windowInSeconds(),
                distributedRateLimit.limit(),
                distributedRateLimit.algorithm(),
                distributedRateLimit.fallbackMethod());
    }

    private Object doEnforce(
            ProceedingJoinPoint joinPoint,
            String evaluatedKey,
            int capacity,
            int refillRate,
            int windowSizeSeconds,
            int maxRequests,
            Algorithm targetAlgorithm,
            String fallbackMethodName) throws Throwable {
        if (!properties.isEnabled()) {
            return joinPoint.proceed();
        }

        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes != null ? attributes.getRequest() : null;
        HttpServletResponse response = attributes != null ? attributes.getResponse() : null;

        Algorithm algorithm = (targetAlgorithm == Algorithm.DEFAULT)
                ? properties.getDefaultAlgorithm()
                : targetAlgorithm;

        String clientIp = KeyGenerator.extractClientIp(request);
        String identifier = StringUtils.hasText(evaluatedKey) ? evaluatedKey : clientIp;
        String method = request != null ? request.getMethod() : "EXEC";
        String endpoint = request != null ? request.getRequestURI() : joinPoint.getSignature().toShortString();

        String redisKey = KeyGenerator.generateKey(algorithm.name(), identifier, method, endpoint);

        RateLimitResult result = rateLimiterService.tryAcquire(
                redisKey,
                capacity,
                refillRate,
                windowSizeSeconds,
                maxRequests,
                algorithm);

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
        if (StringUtils.hasText(fallbackMethodName)) {
            return invokeFallbackMethod(joinPoint, fallbackMethodName, result);
        }

        throw new RateLimitExceededException(
                result.retryAfterMs(),
                result.remainingTokens(),
                result.algorithm());
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
            } catch (InvocationTargetException ite) {
                throw ite.getTargetException();
            }
        } catch (InvocationTargetException ite) {
            throw ite.getTargetException();
        }
    }
}
