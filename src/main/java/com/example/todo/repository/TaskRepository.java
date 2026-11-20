package com.example.todo.repository;

import com.example.todo.model.Task;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long> {

    // SELECT ... FOR UPDATE: пессимистическая блокировка строки.
    // Используется в эпизоде про deadlock - две транзакции, которые
    // берут одни и те же строки в разном порядке.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Task t where t.id = :id")
    Optional<Task> findWithLockById(@Param("id") Long id);

    // Fetch join: комментарии загружаются в ТОЙ ЖЕ транзакции одним
    // запросом. Правильная альтернатива надежде на OSIV.
    @Query("select t from Task t left join fetch t.comments where t.id = :id")
    Optional<Task> findWithCommentsById(@Param("id") Long id);
}
