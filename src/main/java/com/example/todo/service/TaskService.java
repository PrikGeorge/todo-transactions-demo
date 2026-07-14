package com.example.todo.service;

import com.example.todo.model.Task;
import com.example.todo.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TaskService {

    private final TaskRepository taskRepository;

    public TaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    // readOnly = true - это не только семантика: Hibernate отключает
    // dirty checking, а драйвер может использовать реплики.
    // Дешёвая транзакция для дешёвой операции.
    @Transactional(readOnly = true)
    public List<Task> findAll() {
        return taskRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Task findById(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Task findWithComments(Long id) {
        return taskRepository.findWithCommentsById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));
    }

    // Транзакция ровно вокруг работы с БД. Никаких внешних вызовов,
    // никакого ожидания - взял соединение, поработал, вернул.
    @Transactional(timeout = 5)
    public Task create(String title) {
        return taskRepository.save(new Task(title));
    }

    public static class TaskNotFoundException extends RuntimeException {
        public TaskNotFoundException(Long id) {
            super("Task not found: " + id);
        }
    }
}
