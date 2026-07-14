package com.example.todo.crime;

import com.example.todo.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ПОДОЗРЕВАЕМЫЙ №4: deadlock, или дуэль.
 *
 * Две транзакции блокируют одни и те же строки в РАЗНОМ порядке:
 * первая держит A и ждёт B, вторая держит B и ждёт A. Вечно.
 * PostgreSQL через ~1 секунду (deadlock_timeout) выбирает жертву
 * и убивает её транзакцию: "ERROR: deadlock detected".
 *
 * Лекарство скучное: блокировать строки ВСЕГДА в одном порядке
 * (например, по возрастанию id). Дуэль невозможна, если оба
 * стреляют в одну сторону.
 */
@Service
public class DeadlockService {

    private static final Logger log = LoggerFactory.getLogger(DeadlockService.class);

    private final TaskRepository taskRepository;
    private final TransactionTemplate transactionTemplate;
    private final ExecutorService duelists = Executors.newFixedThreadPool(2);

    public DeadlockService(TaskRepository taskRepository,
                           TransactionTemplate transactionTemplate) {
        this.taskRepository = taskRepository;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * @param ordered false - блокируем в разном порядке (deadlock),
     *                true  - в одинаковом порядке (оба выживают)
     */
    public Map<String, Object> duel(long idA, long idB, boolean ordered) {
        long firstForTx2 = ordered ? Math.min(idA, idB) : idB;
        long secondForTx2 = ordered ? Math.max(idA, idB) : idA;
        long firstForTx1 = ordered ? Math.min(idA, idB) : idA;
        long secondForTx1 = ordered ? Math.max(idA, idB) : idB;

        CompletableFuture<String> tx1 = CompletableFuture.supplyAsync(
                () -> lockInOrder("tx-1", firstForTx1, secondForTx1), duelists);
        CompletableFuture<String> tx2 = CompletableFuture.supplyAsync(
                () -> lockInOrder("tx-2", firstForTx2, secondForTx2), duelists);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("lockOrdering", ordered ? "одинаковый порядок (fix)" : "встречный порядок (дуэль)");
        report.put("tx-1", tx1.join());
        report.put("tx-2", tx2.join());
        return report;
    }

    private String lockInOrder(String name, long firstId, long secondId) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                taskRepository.findWithLockById(firstId)
                        .orElseThrow(() -> new IllegalStateException("Task " + firstId + " not found"));
                log.info("[{}] захватил блокировку задачи {}, иду за задачей {}",
                        name, firstId, secondId);

                sleep(500);  // даём второй транзакции время захватить свою строку

                taskRepository.findWithLockById(secondId)
                        .orElseThrow(() -> new IllegalStateException("Task " + secondId + " not found"));
                log.info("[{}] захватил обе блокировки", name);
            });
            return "выжил: заблокировал " + firstId + ", затем " + secondId;
        } catch (Exception e) {
            log.warn("[{}] погиб: {}", name, e.getMessage());
            return "погиб: " + rootCauseMessage(e);
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

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
