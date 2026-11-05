package com.gateflow.annotation;

import com.gateflow.model.Algorithm;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Distributed rate limit annotation supporting Spring Expression Language (SpEL)
 * for dynamic keys (e.g. key = "#userId", "#request.getHeader('X-API-KEY')").
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DistributedRateLimit {

    /**
     * SpEL expression or static key prefix (e.g. "#userId", "#dto.username", "login_action").
     * If left blank, client IP is used.
     */
    String key() default "";

    /**
     * Rate limit quota (requests allowed per window).
     */
    int limit() default 10;

    /**
     * Window duration in seconds.
     */
    int windowInSeconds() default 60;

    /**
     * Rate limiting algorithm strategy (Defaults to SLIDING_WINDOW as per blueprint).
     */
    Algorithm algorithm() default Algorithm.SLIDING_WINDOW;

    /**
     * Optional fallback method name to invoke upon rate limit breach.
     */
    String fallbackMethod() default "";
}

