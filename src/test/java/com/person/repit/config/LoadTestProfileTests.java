package com.person.repit.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("loadtest")
@SpringBootTest
class LoadTestProfileTests {

    @Value("${anthropic.base-url}")
    private String anthropicBaseUrl;

    @Value("${anthropic.api-key}")
    private String anthropicApiKey;

    @Value("${repit.api-server.base-url}")
    private String apiServerBaseUrl;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    @Test
    void usesOnlyLocalLoadTestDependencies() {
        assertThat(anthropicBaseUrl).isEqualTo("http://localhost:8089");
        assertThat(anthropicApiKey).isEqualTo("loadtest-api-key");
        assertThat(apiServerBaseUrl).isEqualTo("http://localhost:8089");
        assertThat(redisPort).isEqualTo(6380);
    }
}
