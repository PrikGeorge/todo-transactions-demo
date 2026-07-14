package com.example.todo.crime;

import com.example.todo.model.Task;
import com.example.todo.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Аудит с хрестоматийным REQUIRES_NEW: запись должна сохраниться,
 * даже если внешняя транзакция откатится. Семантика верная,
 * вызов через отдельный бин - тоже (см. эпизод про self-invocation).
 *
 * Нюанс в цене: REQUIRES_NEW подвешивает внешнюю транзакцию и
 * открывает НОВУЮ физическую - с отдельным соединением из пула.
 * Соединение подвешенной транзакции при этом никуда не возвращается.
 */
@Service
public class AuditTrailService {

    private final TaskRepository taskRepository;

    public AuditTrailService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String event) {
        taskRepository.save(new Task("AUDIT: " + event));
    }
}
