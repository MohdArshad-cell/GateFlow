package com.gateflow.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;

/**
 * Utility for resolving Spring Expression Language (SpEL) expressions
 * dynamically from method invocation parameters.
 */
@Slf4j
public final class SpelEvaluator {

    private static final ExpressionParser PARSER = new SpelExpressionParser();
    private static final ParameterNameDiscoverer PARAM_DISCOVERER = new DefaultParameterNameDiscoverer();

    private SpelEvaluator() {
    }

    /**
     * Evaluates a SpEL key expression (e.g. "#userId", "#dto.id") against method arguments.
     *
     * @param keyExpression SpEL expression string
     * @param method        the invoked method
     * @param args          method argument values
     * @return evaluated string result, or the raw expression if not SpEL
     */
    public static String evaluate(String keyExpression, Method method, Object[] args) {
        if (!StringUtils.hasText(keyExpression)) {
            return "";
        }

        // If not a SpEL expression, return as literal string
        if (!keyExpression.contains("#") && !keyExpression.contains("'") && !keyExpression.contains("T(")) {
            return keyExpression;
        }

        try {
            String[] paramNames = PARAM_DISCOVERER.getParameterNames(method);
            EvaluationContext context = new StandardEvaluationContext();

            if (paramNames != null && args != null) {
                for (int i = 0; i < paramNames.length && i < args.length; i++) {
                    context.setVariable(paramNames[i], args[i]);
                }
            }

            Expression expression = PARSER.parseExpression(keyExpression);
            Object value = expression.getValue(context);
            return value != null ? value.toString() : "";
        } catch (Exception ex) {
            log.warn("Failed to evaluate SpEL expression '{}' on method {}: {}", keyExpression, method.getName(), ex.getMessage());
            return keyExpression;
        }
    }
}
