package com.example.todo.crime;

import com.example.todo.model.Task;
import com.example.todo.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * ПОДОЗРЕВАЕМЫЙ №5: REQUIRES_NEW под открытой транзакцией.
 *
 * Внешняя транзакция держит соединение. REQUIRES_NEW подвешивает её
 * и просит у пула ВТОРОЕ соединение. Один такой запрос - две брони.
 * Когда параллельных запросов столько же, сколько соединений в пуле,
 * все внешние транзакции держат по соединению и все внутренние ждут
 * второго. Свободных нет и не будет: пул задушил сам себя.
 *
 * Лекарство: не открывать REQUIRES_NEW под транзакцией, которая
 * держит соединение. Аудит - последовательной транзакцией после
 * основной, а не матрёшкой внутри неё.
 */
@Service
public class RequiresNewService {

    private static final Logger log = LoggerFactory.getLogger(RequiresNewService.class);

    private final TaskRepository taskRepository;
    private final AuditTrailService auditTrailService;
    private final TransactionTemplate transactionTemplate;
    private final ExecutorService gang = Executors.newFixedThreadPool(10);

    public RequiresNewService(TaskRepository taskRepository,
                              AuditTrailService auditTrailService,
                              TransactionTemplate transactionTemplate) {
        this.taskRepository = taskRepository;
        this.auditTrailService = auditTrailService;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * @param robbers сколько параллельных запросов запустить
     *                (при robbers >= размера пула ограбление проваливается целиком)
     * @param fixed   false - аудит REQUIRES_NEW внутри открытой транзакции (матрёшка),
     *                true  - аудит отдельной транзакцией после основной
     */
    public Map<String, Object> raid(int robbers, boolean fixed) {
        // Барьер: аудит стартует, только когда ВСЕ внешние транзакции
        // уже заняли по соединению. Так сценарий воспроизводится
        // детерминированно, а не в зависимости от прогрева пула.
        CyclicBarrier everyoneSeated = new CyclicBarrier(robbers);

        List<CompletableFuture<String>> results = new ArrayList<>();
        for (int i = 1; i <= robbers; i++) {
            String name = "tx-" + i;
            results.add(CompletableFuture.supplyAsync(
                    () -> fixed ? bookAndAuditFixed(name, everyoneSeated)
                                : bookAndAuditNested(name, everyoneSeated), gang));
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("mode", fixed ? "аудит после коммита (fix)" : "REQUIRES_NEW внутри транзакции (матрёшка)");
        report.put("robbers", robbers);
        for (int i = 0; i < robbers; i++) {
            report.put("tx-" + (i + 1), results.get(i).join());
        }
        return report;
    }

    // ПРЕСТУПЛЕНИЕ: внешняя транзакция держит соединение,
    // а REQUIRES_NEW внутри неё требует у пула второе.
    private String bookAndAuditNested(String name, CyclicBarrier everyoneSeated) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                taskRepository.save(new Task("Улика от " + name));
                await(everyoneSeated);  // все заняли по столику
                auditTrailService.record(name);  // <- нужно ВТОРОЕ соединение
            });
            return "выжил: аудит записан";
        } catch (Exception e) {
            log.warn("[{}] погиб: {}", name, e.getMessage());
            return "погиб: " + rootCauseMessage(e);
        }
    }

    // Как надо: основная транзакция закоммитилась и вернула соединение,
    // аудит открывает свою транзакцию следом. В моменте - одно соединение
    // на запрос, а не два.
    private String bookAndAuditFixed(String name, CyclicBarrier everyoneSeated) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                taskRepository.save(new Task("Улика от " + name));
                await(everyoneSeated);  // та же толпа, но второй столик не нужен
            });
            auditTrailService.record(name);  // <- соединение уже вернулось в пул
            return "выжил: аудит записан";
        } catch (Exception e) {
            log.warn("[{}] погиб: {}", name, e.getMessage());
            return "погиб: " + rootCauseMessage(e);
        }
    }

    private static void await(CyclicBarrier barrier) {
        try {
            // Таймаут - страховка: если robbers больше размера пула,
            // до барьера дойдут не все, и ждать их вечно не нужно.
            barrier.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Прерван у барьера", e);
        } catch (Exception e) {
            throw new IllegalStateException("Не все добрались до барьера: " + e, e);
        }
    }

    private static String rootCauseMessage(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return message == null ? cause.getClass().getSimpleName()
                : message.lines().findFirst().orElse(message);
    }
}
