# TODO Transactions Demo

Демо-проект к статье **«Дело о зависшем пуле: @Transactional, connection leak и deadlock в Spring Boot. Production-нуар»**.

Показывает, как типовые ошибки работы с транзакциями и соединениями вешают connection pool, и как каждую из них ловить и чинить:

- **Connection leak** - соединение взяли у DataSource и не вернули
- **Медленная транзакция** - внешний вызов внутри `@Transactional`
- **Self-invocation** - `@Transactional` молча игнорируется при вызове через `this`
- **Deadlock** - блокировки одних строк во встречном порядке
- **REQUIRES_NEW под открытой транзакцией** - каждому запросу нужно два соединения, пул душит сам себя
- **Open Session In View** - LAZY-загрузка вне транзакции

Каждая проблема - отдельный эндпоинт `/api/crime/*`, рядом лежит исправленная версия `*-fixed`.

## Стек

- Java 17, Spring Boot 3.2 (Web, Data JPA, Actuator)
- PostgreSQL 16 (настоящие блокировки и deadlock detector)
- HikariCP (пул намеренно маленький: 5 соединений, connection-timeout 5s)
- Prometheus + Grafana (метрики пула, алерты, дашборд)

## Быстрый старт

```bash
docker-compose up -d
```

| Сервис | Адрес |
|--------|-------|
| Приложение | http://localhost:8080 |
| Метрики | http://localhost:8080/actuator/prometheus |
| Prometheus | http://localhost:9090 |
| Grafana (admin/admin) | http://localhost:3000 - дашборд «Bar 'U Hikari'» |
| PostgreSQL | localhost:55432 (todo/todo) |

Локальный запуск без Docker (нужен только Postgres из compose):

```bash
docker-compose up -d postgres
./mvnw spring-boot:run
```

## Воспроизведение преступлений

Состояние пула в любой момент:

```bash
curl http://localhost:8080/api/crime/pool
# {"active":0,"idle":2,"waiting":0,"total":5,"leaked":0}
```

### 1. Connection leak

```bash
# Пять вызовов - пул исчерпан
curl -X POST http://localhost:8080/api/crime/leak   # повторить 5 раз

curl http://localhost:8080/api/tasks
# ~5 секунд ожидания -> 500 (SQLTransientConnectionException)

# Через 10 секунд в логе приложения появится WARN от Hikari:
# "Apparent connection leak detected" со stack trace виновника

# Вернуть украденные соединения (чтобы не перезапускать приложение)
curl -X POST http://localhost:8080/api/crime/leak/release
```

Исправленная версия (try-with-resources): `POST /api/crime/leak-fixed`.

### 2. Медленная транзакция

```bash
# Внешний вызов (5s) внутри @Transactional. Пять параллельных
# запросов занимают весь пул:
for i in $(seq 5); do curl -s -X POST \
  http://localhost:8080/api/crime/slow-tx & done

curl http://localhost:8080/api/crime/pool
# {"active":5,"idle":0,...}
```

Исправленная версия (TransactionTemplate, внешний вызов после коммита): `POST /api/crime/slow-tx-fixed`.

### 3. Self-invocation

```bash
curl http://localhost:8080/api/crime/self-invocation
# {"transactionActive":false,...} - аннотация проигнорирована

curl http://localhost:8080/api/crime/self-invocation-fixed
# {"transactionActive":true,...} - вызов через отдельный бин
```

Этот сценарий зафиксирован тестом `SelfInvocationTest`.

### 4. Deadlock

```bash
curl -X POST "http://localhost:8080/api/crime/deadlock?idA=1&idB=2"
# {"tx-1":"выжил...","tx-2":"погиб: ERROR: deadlock detected"}

curl -X POST "http://localhost:8080/api/crime/deadlock-fixed?idA=1&idB=2"
# оба выжили: блокировки берутся в одном порядке
```

### 5. REQUIRES_NEW под открытой транзакцией

```bash
# Пять параллельных запросов, каждый: транзакция держит соединение,
# внутри - аудит с REQUIRES_NEW, которому нужно ВТОРОЕ соединение.
# Все пять внешних держат по столику, все пять внутренних ждут второго.
curl -X POST "http://localhost:8080/api/crime/requires-new?robbers=5"
# ~5 секунд - и обычно 3-5 из 5 "погиб: Connection is not available..."
# по connection-timeout. 1-2 потока могут выжить: когда первый упавший
# откатывает внешнюю транзакцию, его коннект успевает подхватить сосед,
# у которого таймаут ещё не истёк.

curl -X POST "http://localhost:8080/api/crime/requires-new-fixed?robbers=5"
# все выжили: аудит идёт отдельной транзакцией ПОСЛЕ основной
```

### 6. Open Session In View

Проект запускается с `spring.jpa.open-in-view: false`:

```bash
curl http://localhost:8080/api/crime/osiv/1
# 500: LazyInitializationException - LAZY-коллекция сериализуется вне транзакции

curl http://localhost:8080/api/crime/osiv-fixed/1
# 200: комментарии загружены fetch join'ом внутри транзакции
```

Чтобы увидеть, как OSIV «прячет» проблему, раскомментируйте
`SPRING_JPA_OPEN_IN_VIEW=true` в docker-compose.yml и повторите первый запрос.

## Диагностика

```bash
# Thread dump без JDK-утилит (в eclipse-temurin:17-jre-alpine нет jstack):
# JVM получает SIGQUIT и печатает дамп в stdout контейнера
docker exec todo-tx-app kill -3 1
docker logs todo-tx-app

# Thread dump в отдельный файл: соседний JDK-контейнер в том же PID-неймспейсе
docker run --rm --pid=container:todo-tx-app --user 1001:1001 \
  eclipse-temurin:17-jdk jstack 1 > threads.txt

# Показания базы: кто держит транзакции
docker exec -it todo-postgres psql -U todo -d todo -c \
  "SELECT pid, state, now() - xact_start AS tx_age, left(query, 60) AS query
   FROM pg_stat_activity WHERE datname = 'todo' ORDER BY xact_start;"
```

Алерты Prometheus (`prometheus/alert-rules.yml`): очередь за соединениями,
connection timeouts, аномально долгое удержание соединений.

## Тесты

```bash
./mvnw test
```

- `SelfInvocationTest` - вызов через `this` не открывает транзакцию, через прокси - открывает
- `PoolMetricsExposureTest` - метрики `hikaricp_connections_*` экспортируются в `/actuator/prometheus`
