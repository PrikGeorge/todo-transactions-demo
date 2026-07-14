package com.example.todo.crime;

import com.example.todo.model.Task;
import com.example.todo.repository.TaskRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Отдельный бин: вызов приходит снаружи, через прокси,
 * поэтому @Transactional здесь действительно работает.
 */
@Component
public class TransactionalWorker {

    private final TaskRepository taskRepository;

    public TransactionalWorker(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Transactional
    public boolean saveWithTransaction() {
        taskRepository.save(new Task("Сохранено через отдельный бин"));
        return TransactionSynchronizationManager.isActualTransactionActive();
    }
}
