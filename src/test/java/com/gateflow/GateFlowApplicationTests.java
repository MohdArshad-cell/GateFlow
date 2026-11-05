package com.gateflow;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class GateFlowApplicationTests {

    @MockBean
    private RedisConnectionFactory redisConnectionFactory;

    @Test
    void contextLoads() {
        // Verifies Spring context initializes all beans and configurations correctly
    }
}

