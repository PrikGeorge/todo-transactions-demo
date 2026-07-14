package com.example.todo.external;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.example.todo.model.Task;

/**
 * Имитация внешней системы: сервис уведомлений, платёжный шлюз,
 * чужой REST API - неважно. Важно одно: он МЕДЛЕННЫЙ, и вы
 * его не контролируете.
 */
@Component
public class SlowExternalClient {

    private static final Logger log = LoggerFactory.getLogger(SlowExternalClient.class);

    private final long delayMs;

    public SlowExternalClient(@Value("${demo.external-delay-ms}") long delayMs) {
        this.delayMs = delayMs;
    }

    public void notifyExternalSystem(Task task) {
        log.info("Уведомляем внешнюю систему о задаче {} (это займёт {} мс)...",
                task.getId(), delayMs);
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while calling external system", e);
        }
        log.info("Внешняя система уведомлена о задаче {}", task.getId());
    }
}
