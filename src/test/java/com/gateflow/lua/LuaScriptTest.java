package com.gateflow.lua;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class LuaScriptTest {

    @Test
    @DisplayName("Verify token_bucket.lua script exists, is loadable, and defines core operations")
    void testTokenBucketScriptIntegrity() throws IOException {
        ClassPathResource resource = new ClassPathResource("scripts/token_bucket.lua");
        assertThat(resource.exists()).isTrue();

        String scriptContent = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(scriptContent).contains("HMGET");
        assertThat(scriptContent).contains("HMSET");
        assertThat(scriptContent).contains("EXPIRE");
        assertThat(scriptContent).contains("tokens_to_add");
    }

    @Test
    @DisplayName("Verify sliding_window.lua script exists, is loadable, and defines ZSET operations")
    void testSlidingWindowScriptIntegrity() throws IOException {
        ClassPathResource resource = new ClassPathResource("scripts/sliding_window.lua");
        assertThat(resource.exists()).isTrue();

        String scriptContent = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(scriptContent).contains("ZREMRANGEBYSCORE");
        assertThat(scriptContent).contains("ZCARD");
        assertThat(scriptContent).contains("ZADD");
        assertThat(scriptContent).contains("ZRANGE");
    }
}

