package com.example.todo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Метрики пула - главные свидетели по этому делу. Тест проверяет,
 * что они действительно экспортируются: алерты и дашборд опираются
 * на hikaricp_connections_*.
 *
 * @AutoConfigureObservability обязательна: в тестах Spring Boot
 * отключает экспорт метрик, и без неё /actuator/prometheus вернёт 404.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureObservability
@ActiveProfiles("test")
class PoolMetricsExposureTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("HikariCP-метрики экспортируются в /actuator/prometheus")
    void hikariMetricsAreExposed() {
        ResponseEntity<String> response =
                restTemplate.getForEntity("/actuator/prometheus", String.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        String body = response.getBody();
        assertThat(body).contains("hikaricp_connections_active");
        assertThat(body).contains("hikaricp_connections_pending");
        assertThat(body).contains("hikaricp_connections_timeout_total");
        assertThat(body).contains("pool=\"TodoHikariPool\"");
    }
}
