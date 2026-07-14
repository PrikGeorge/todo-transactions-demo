package com.example.todo.controller;

import com.example.todo.model.Task;
import com.example.todo.model.TaskStatus;
import com.example.todo.service.TaskService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    public List<TaskResponse> findAll() {
        return taskService.findAll().stream().map(TaskResponse::from).toList();
    }

    @GetMapping("/{id}")
    public TaskResponse findById(@PathVariable Long id) {
        return TaskResponse.from(taskService.findById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse create(@Valid @RequestBody CreateTaskRequest request) {
        return TaskResponse.from(taskService.create(request.title()));
    }

    public record CreateTaskRequest(@NotBlank String title) {
    }

    // Наружу отдаём DTO, а не entity: у Task есть LAZY-коллекция comments,
    // и её сериализация вне транзакции - в точности "дело о дворецком"
    // (см. /api/crime/osiv/{id}, где эта ошибка воспроизводится намеренно).
    public record TaskResponse(Long id, String title, TaskStatus status, LocalDateTime createdAt) {

        static TaskResponse from(Task task) {
            return new TaskResponse(task.getId(), task.getTitle(), task.getStatus(), task.getCreatedAt());
        }
    }
}
