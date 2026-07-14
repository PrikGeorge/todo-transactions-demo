package com.example.todo.demo;

import com.example.todo.model.Task;
import com.example.todo.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Стартовые данные: две задачи для дуэли (deadlock) и комментарии
 * для эпизода про OSIV.
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final TaskRepository taskRepository;

    public DataInitializer(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (taskRepository.count() > 0) {
            return;
        }

        Task first = new Task("Проверить бар «У Хикари»");
        first.addComment("Десять столиков, очередь на входе");
        first.addComment("Бармен клянётся, что все соединения возвращают");

        Task second = new Task("Допросить @Transactional");
        second.addComment("Подозревается в фальшивом алиби");

        taskRepository.save(first);
        taskRepository.save(second);

        log.info("Созданы стартовые задачи: {} и {}", first.getId(), second.getId());
    }
}
