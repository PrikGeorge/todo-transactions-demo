package com.example.todo.crime;

import com.example.todo.model.Task;
import com.example.todo.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ПОДОЗРЕВАЕМЫЙ №3: self-invocation, или фальшивое алиби.
 *
 * @Transactional работает через прокси: Spring оборачивает бин, и
 * транзакцию открывает ОБЁРТКА, а не сам метод. Вызов через this
 * идёт мимо обёртки - аннотация молча игнорируется. Метод убеждён,
 * что у него есть транзакция. У него её нет.
 */
@Service
public class SelfInvocationService {

    private final TaskRepository taskRepository;
    private final TransactionalWorker worker;

    public SelfInvocationService(TaskRepository taskRepository, TransactionalWorker worker) {
        this.taskRepository = taskRepository;
        this.worker = worker;
    }

    public Map<String, Object> demonstrateBrokenAlibi() {
        Map<String, Object> report = new LinkedHashMap<>();

        // ПРЕСТУПЛЕНИЕ: вызов @Transactional-метода через this.
        // Прокси не участвует, транзакция не открывается.
        boolean txActive = this.saveWithTransaction();
        report.put("call", "this.saveWithTransaction()");
        report.put("transactionActive", txActive);
        report.put("verdict", txActive
                ? "транзакция есть (не должно случиться)"
                : "аннотация @Transactional молча проигнорирована");
        return report;
    }

    public Map<String, Object> demonstrateRealAlibi() {
        Map<String, Object> report = new LinkedHashMap<>();

        // Как надо: вызов через ДРУГОЙ бин - прокси на месте,
        // транзакция открывается.
        boolean txActive = worker.saveWithTransaction();
        report.put("call", "worker.saveWithTransaction()");
        report.put("transactionActive", txActive);
        report.put("verdict", txActive
                ? "транзакция открыта, как и обещала аннотация"
                : "транзакции нет (не должно случиться)");
        return report;
    }

    @Transactional
    public boolean saveWithTransaction() {
        taskRepository.save(new Task("Жертва self-invocation"));
        return TransactionSynchronizationManager.isActualTransactionActive();
    }
}
