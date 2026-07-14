package com.example.todo.crime;

import com.example.todo.external.SlowExternalClient;
import com.example.todo.model.Task;
import com.example.todo.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * ПОДОЗРЕВАЕМЫЙ №2: медленная транзакция.
 *
 * Вставка в БД занимает миллисекунды. Но транзакция держит соединение
 * до конца метода - включая 5 секунд ожидания внешней системы.
 * 5 параллельных запросов = весь пул занят ожиданием чужого API.
 */
@Service
public class SlowTransactionService {

    private final TaskRepository taskRepository;
    private final SlowExternalClient externalClient;
    private final TransactionTemplate transactionTemplate;

    public SlowTransactionService(TaskRepository taskRepository,
                                  SlowExternalClient externalClient,
                                  TransactionTemplate transactionTemplate) {
        this.taskRepository = taskRepository;
        this.externalClient = externalClient;
        this.transactionTemplate = transactionTemplate;
    }

    // ПРЕСТУПЛЕНИЕ: внешний вызов ВНУТРИ транзакции.
    // Соединение с БД занято все 5 секунд, хотя БД нужна была 5 миллисекунд.
    @Transactional
    public Task createAndNotifySlow(String title) {
        Task saved = taskRepository.save(new Task(title));
        externalClient.notifyExternalSystem(saved);  // <- держим соединение зря
        return saved;
    }

    // Как надо: транзакция ровно вокруг работы с БД.
    // TransactionTemplate делает границы явными и узкими.
    // Внешний вызов - после коммита, соединение уже возвращено в пул.
    public Task createAndNotifyFixed(String title) {
        Task saved = transactionTemplate.execute(status ->
                taskRepository.save(new Task(title)));
        externalClient.notifyExternalSystem(saved);  // <- соединение уже в пуле
        return saved;
    }
}
