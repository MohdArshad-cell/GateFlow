package com.gateflow.annotation;

import com.gateflow.model.Algorithm;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to enforce distributed rate limiting on specific Spring MVC handler methods.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /**
     * Custom key prefix or business identifier (e.g. "login", "user_profile").
     * If left blank, KeyGenerator automatically builds a key from client IP + HTTP method + endpoint path.
     */
    String key() default "";

    /**
     * Maximum capacity for Token Bucket.
     * 0 means use application.yml configured default.
     */
    int capacity() default 0;

    /**
     * Refill rate (tokens added per second) for Token Bucket.
     * 0 means use application.yml configured default.
     */
    int refillRate() default 0;

    /**
     * Sliding window duration in seconds.
     * 0 means use application.yml configured default.
     */
    int windowSizeSeconds() default 0;

    /**
     * Maximum allowed requests in the sliding window.
     * 0 means use application.yml configured default.
     */
    int maxRequests() default 0;

    /**
     * Rate limiting algorithm strategy to apply.
     * DEFAULT inherits application.yml configuration.
     */
    Algorithm algorithm() default Algorithm.DEFAULT;

    /**
     * Optional name of a fallback method declared on the same controller.
     * If invoked, must match signature or return a compatible response.
     */
    String fallbackMethod() default "";
}

