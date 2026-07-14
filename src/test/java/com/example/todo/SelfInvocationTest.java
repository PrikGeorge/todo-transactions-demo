package com.example.todo;

import com.example.todo.crime.SelfInvocationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Тест фиксирует главную улику эпизода про self-invocation:
 * вызов @Transactional-метода через this транзакцию НЕ открывает,
 * а вызов через отдельный бин - открывает.
 */
@SpringBootTest
@ActiveProfiles("test")
class SelfInvocationTest {

    @Autowired
    private SelfInvocationService selfInvocationService;

    @Test
    @DisplayName("Вызов через this: @Transactional молча игнорируется")
    void selfInvocationIgnoresTransactionalAnnotation() {
        Map<String, Object> report = selfInvocationService.demonstrateBrokenAlibi();

        assertThat(report.get("transactionActive")).isEqualTo(false);
    }

    @Test
    @DisplayName("Вызов через отдельный бин: транзакция открывается")
    void callThroughProxyOpensTransaction() {
        Map<String, Object> report = selfInvocationService.demonstrateRealAlibi();

        assertThat(report.get("transactionActive")).isEqualTo(true);
    }
}
