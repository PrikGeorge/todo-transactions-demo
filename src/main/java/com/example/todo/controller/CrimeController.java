package com.example.todo.controller;

import com.example.todo.crime.DeadlockService;
import com.example.todo.crime.LeakyReportService;
import com.example.todo.crime.RequiresNewService;
import com.example.todo.crime.SelfInvocationService;
import com.example.todo.crime.SlowTransactionService;
import com.example.todo.model.Task;
import com.example.todo.service.TaskService;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Место преступления. Каждый эндпоинт воспроизводит одну из проблем
 * из статьи. НЕ копируйте этот код в production - он написан,
 * чтобы ломать, и делает это хорошо.
 */
@RestController
@RequestMapping("/api/crime")
public class CrimeController {

    private final LeakyReportService leakyReportService;
    private final SlowTransactionService slowTransactionService;
    private final SelfInvocationService selfInvocationService;
    private final DeadlockService deadlockService;
    private final RequiresNewService requiresNewService;
    private final TaskService taskService;
    private final DataSource dataSource;

    public CrimeController(LeakyReportService leakyReportService,
                           SlowTransactionService slowTransactionService,
                           SelfInvocationService selfInvocationService,
                           DeadlockService deadlockService,
                           RequiresNewService requiresNewService,
                           TaskService taskService,
                           DataSource dataSource) {
        this.leakyReportService = leakyReportService;
        this.slowTransactionService = slowTransactionService;
        this.selfInvocationService = selfInvocationService;
        this.deadlockService = deadlockService;
        this.requiresNewService = requiresNewService;
        this.taskService = taskService;
        this.dataSource = dataSource;
    }

    // ---------- Состояние пула: быстрый осмотр места ----------

    @GetMapping("/pool")
    public Map<String, Object> poolState() throws SQLException {
        HikariPoolMXBean pool = dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean();
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("active", pool.getActiveConnections());
        state.put("idle", pool.getIdleConnections());
        state.put("waiting", pool.getThreadsAwaitingConnection());
        state.put("total", pool.getTotalConnections());
        state.put("leaked", leakyReportService.leakedCount());
        return state;
    }

    // ---------- Подозреваемый №1: connection leak ----------

    @PostMapping("/leak")
    public Map<String, Object> leak() throws SQLException {
        long count = leakyReportService.generateReportWithLeak();
        return Map.of(
                "tasksInReport", count,
                "leakedConnections", leakyReportService.leakedCount(),
                "hint", "5 вызовов - и пул пуст. Смотрите WARN от Hikari через 10 секунд");
    }

    @PostMapping("/leak-fixed")
    public Map<String, Object> leakFixed() throws SQLException {
        long count = leakyReportService.generateReportProperly();
        return Map.of("tasksInReport", count, "leakedConnections", leakyReportService.leakedCount());
    }

    @PostMapping("/leak/release")
    public Map<String, Object> releaseLeaked() {
        return Map.of("released", leakyReportService.releaseLeaked());
    }

    // ---------- Подозреваемый №2: медленная транзакция ----------

    @PostMapping("/slow-tx")
    public Task slowTransaction() {
        return slowTransactionService.createAndNotifySlow("Задача из медленной транзакции");
    }

    @PostMapping("/slow-tx-fixed")
    public Task slowTransactionFixed() {
        return slowTransactionService.createAndNotifyFixed("Задача с транзакцией по делу");
    }

    // ---------- Подозреваемый №3: self-invocation ----------

    @GetMapping("/self-invocation")
    public Map<String, Object> selfInvocation() {
        return selfInvocationService.demonstrateBrokenAlibi();
    }

    @GetMapping("/self-invocation-fixed")
    public Map<String, Object> selfInvocationFixed() {
        return selfInvocationService.demonstrateRealAlibi();
    }

    // ---------- Подозреваемый №4: deadlock ----------

    @PostMapping("/deadlock")
    public Map<String, Object> deadlock(@RequestParam(defaultValue = "1") long idA,
                                        @RequestParam(defaultValue = "2") long idB) {
        return deadlockService.duel(idA, idB, false);
    }

    @PostMapping("/deadlock-fixed")
    public Map<String, Object> deadlockFixed(@RequestParam(defaultValue = "1") long idA,
                                             @RequestParam(defaultValue = "2") long idB) {
        return deadlockService.duel(idA, idB, true);
    }

    // ---------- Подозреваемый №5: REQUIRES_NEW под открытой транзакцией ----------

    // Каждый запрос держит соединение внешней транзакции и просит у пула
    // второе для REQUIRES_NEW. При robbers = размеру пула свободных
    // соединений не остаётся ни для кого - пул душит сам себя.
    @PostMapping("/requires-new")
    public Map<String, Object> requiresNew(@RequestParam(defaultValue = "5") int robbers) {
        return requiresNewService.raid(robbers, false);
    }

    // Как надо: основная транзакция закоммитилась и вернула соединение,
    // аудит открывает свою транзакцию следом.
    @PostMapping("/requires-new-fixed")
    public Map<String, Object> requiresNewFixed(@RequestParam(defaultValue = "5") int robbers) {
        return requiresNewService.raid(robbers, true);
    }

    // ---------- Дворецкий: Open Session In View ----------

    // Сервис возвращает entity с LAZY-коллекцией, транзакция уже закрыта.
    // Jackson сериализует comments ВНЕ транзакции:
    //  - open-in-view: false -> LazyInitializationException (честное признание)
    //  - open-in-view: true  -> "работает", но сессия живёт весь запрос
    @GetMapping("/osiv/{id}")
    public Task osiv(@PathVariable Long id) {
        return taskService.findById(id);
    }

    // Как надо: fetch join загружает комментарии в ТОЙ ЖЕ транзакции.
    // Сериализации нечего лениво подгружать.
    @GetMapping("/osiv-fixed/{id}")
    public Task osivFixed(@PathVariable Long id) {
        return taskService.findWithComments(id);
    }
}
