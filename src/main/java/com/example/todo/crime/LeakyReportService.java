package com.example.todo.crime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * ПОДОЗРЕВАЕМЫЙ №1: connection leak.
 *
 * "Легаси-отчёт", который берёт соединение напрямую у DataSource
 * и не возвращает. Каждый вызов - минус одно соединение из пула.
 * Пул из 5 соединений заканчивается за 5 вызовов, после чего
 * приложение перестаёт отвечать. Всё ещё живое. Уже бесполезное.
 *
 * Улику фиксирует leak-detection-threshold в application.yml:
 * через 10 секунд Hikari напишет WARN со stack trace виновника.
 */
@Service
public class LeakyReportService {

    private static final Logger log = LoggerFactory.getLogger(LeakyReportService.class);

    private final DataSource dataSource;

    // Храним украденные соединения, чтобы утечка была честной:
    // никто их не закроет и не соберёт.
    private final List<Connection> stolenConnections = new CopyOnWriteArrayList<>();

    public LeakyReportService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public long generateReportWithLeak() throws SQLException {
        // ПРЕСТУПЛЕНИЕ: нет try-with-resources. Соединение взяли - не вернули.
        Connection connection = dataSource.getConnection();
        stolenConnections.add(connection);

        Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery("select count(*) from tasks");
        resultSet.next();
        long count = resultSet.getLong(1);

        log.warn("Отчёт готов: {} задач. Соединение НЕ возвращено (утекло уже {})",
                count, stolenConnections.size());
        return count;
    }

    public long generateReportProperly() throws SQLException {
        // Как надо: try-with-resources закрывает всё в обратном порядке,
        // даже при исключении. Соединение возвращается в пул всегда.
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("select count(*) from tasks")) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    public int leakedCount() {
        return stolenConnections.size();
    }

    /** Вернуть украденное - чтобы демо можно было повторять без рестарта. */
    public int releaseLeaked() {
        int released = 0;
        for (Connection connection : stolenConnections) {
            try {
                connection.close();
                released++;
            } catch (SQLException e) {
                log.error("Не удалось вернуть соединение", e);
            }
        }
        stolenConnections.clear();
        return released;
    }
}
