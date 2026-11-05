package com.gateflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.List;

/**
 * Spring Data Redis configuration initializing high-throughput string templates
 * and pre-compiling Lua scripts as atomic RedisScript beans.
 */
@Configuration
public class RedisConfig {

    @Bean
    @Primary
    public RedisTemplate<String, String> rateLimitRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer serializer = new StringRedisSerializer();
        template.setKeySerializer(serializer);
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(serializer);
        template.setHashValueSerializer(serializer);

        template.afterPropertiesSet();
        return template;
    }

    @Bean
    @SuppressWarnings("unchecked")
    public RedisScript<List<Long>> tokenBucketScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("scripts/token_bucket.lua"));
        script.setResultType(List.class);
        return (RedisScript<List<Long>>) (RedisScript<?>) script;
    }

    @Bean
    @SuppressWarnings("unchecked")
    public RedisScript<List<Long>> slidingWindowScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("scripts/sliding_window.lua"));
        script.setResultType(List.class);
        return (RedisScript<List<Long>>) (RedisScript<?>) script;
    }
}
