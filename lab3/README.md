# Лабораторная №3 — максимально простой вариант

**Java 17 + Spring Boot + файловая реляционная H2.**

Вся программа написана в **3 Java-файлах**:

```text
src/main/java/ru/example/exams/
    ExamApplication.java  — запуск, создание таблиц, БД-пользователя и начальных записей
    ExamController.java   — URL, GET, POST, PATCH, DELETE
    ExamService.java      — SQL-запросы, CRUD и 5 бизнес-операций

src/main/resources/
    schema.sql            — 5 таблиц, связи, проверки, уникальность
    data.sql              — тестовые данные
    application.properties — подключение к H2

postman/
    exams-lab3.postman_collection.json — 46 запросов для полного сценария
```

## Как запустить в IntelliJ IDEA

1. Распакуй архив и открой **папку `exam-db-easy`** как Maven-проект.
2. Нужен **JDK 17 или новее** (лучше 17 или 21).
3. В корне проекта **скопируй** `.env.example` в `.env`.
4. В `.env` замени оба примера паролей на свои:

   ```properties
   DB_ADMIN_PASSWORD=your_admin_password
   DB_PASSWORD=your_app_password
   ```

   Пароли остаются только у тебя. Не отправляй `.env` преподавателю и не добавляй в Git.
5. Запусти `ExamApplication.main()` в IntelliJ. **Рабочая папка запуска должна быть корнем проекта** — там, где лежит `.env`. Или из корня выполни `mvn spring-boot:run`.
6. Открой <http://localhost:8080/api/exams> — появится список экзаменов.

БД создастся по пути `data/exams.mv.db`. **После перезапуска записи сохраняются.** Чтобы начать тесты заново, останови программу и удали папку `data/`.

> Важно: пароль администратора первой созданной БД сохраняется в H2. Если изменишь `DB_ADMIN_PASSWORD`, для этого учебного проекта проще удалить `data/` и создать БД заново.

## Как устроена программа

```text
HTTP-запрос → ExamController → ExamService → JdbcTemplate → H2
```

- `ExamApplication` запускает сервер, создаёт БД и таблицы от администратора `sa`.
- Для обычных запросов приложение использует **EXAM_APP**, у которого права только `SELECT`, `INSERT`, `UPDATE`, `DELETE` на 5 таблиц. Пользователь EXAM_APP не может создавать/удалять таблицы.
- `ExamController` направляет запрос в нужный метод.
- `ExamService` проверяет условия и выполняет SQL.
- `@Transactional` отменяет всю операцию, если что-то пошло не так (например, при изменении двух таблиц).

## Наши пять таблиц

| Таблица | Что хранит | Связи |
|---|---|---|
| `users` | пользователей и количество завершённых экзаменов | имеет попытки |
| `exams` | экзамены | имеет задания и попытки |
| `exam_tasks` | задания экзамена | `exam_id` → `exams` |
| `exam_attempts` | попытки прохождения | `exam_id` → `exams`, `user_id` → `users` |
| `task_solutions` | ответы на задания | `attempt_id` → `exam_attempts`, `task_id` → `exam_tasks` |

Ограничения описаны в `schema.sql`: PRIMARY KEY, FOREIGN KEY, UNIQUE, CHECK, NOT NULL. Например, нельзя дважды начать один экзамен одним пользователем или дважды ответить на одно задание в одной попытке.

## Обычный CRUD

**Все пять сущностей** имеют одинаковые URL:

```text
GET    /api/{type}        — получить все записи
GET    /api/{type}/{id}   — получить одну запись
POST   /api/{type}        — создать
PATCH  /api/{type}/{id}   — изменить
DELETE /api/{type}/{id}   — удалить
```

Вместо `{type}` можно подставить `users`, `exams`, `exam-tasks`, `exam-attempts` или `task-solutions`.

Примеры JSON для создания:

```json
{"name":"Студент"}
```

`POST /api/users`

```json
{"name":"Мой экзамен","passingScore":5,"timeLimitMinutes":30}
```

`POST /api/exams` (новый экзамен будет `DRAFT`)

```json
{"examId":1,"question":"2+2?","correctAnswer":"4","maxScore":10}
```

`POST /api/exam-tasks` (можно добавлять задания только в черновик)

```json
{"examId":1,"userId":1}
```

`POST /api/exam-attempts` (только после публикации)

```json
{"attemptId":1,"taskId":1,"answer":"4"}
```

`POST /api/task-solutions` (только у незавершённой попытки)

Поля `id`, `status`, `points`, время и счётчики устанавливаются программой, а не вручную в POST. Изменение ответа через PATCH пересчитывает баллы. Удаление завершённых попыток запрещено, чтобы не ломать статистику.

## Пять бизнес-операций

1. `POST /api/operations/publish/{examId}` — публикация черновика, только если есть задания и достижим проходной балл.
2. `POST /api/operations/start-attempt` с `{"examId":3,"userId":3}` — запуск попытки только опубликованного экзамена.
3. `POST /api/operations/answer` с `{"attemptId":3,"taskId":5,"answer":"4"}` — сохраняет ответ и **сразу пересчитывает сумму баллов попытки**. Меняются две таблицы: `task_solutions` и `exam_attempts`.
4. `POST /api/operations/finish/{attemptId}` — завершает попытку, только когда есть ответы на все задания, и **увеличивает `users.completed_exams`**. Меняются `exam_attempts` и `users`.
5. `POST /api/operations/archive/{examId}` — архивирует экзамен, если больше нет незавершённых попыток.

## Как проверить в Postman

Импортируй `postman/exams-lab3.postman_collection.json`, затем запусти **Collection Runner** по порядку. Там 46 запросов: CRUD для всех сущностей, пять бизнес-операций и проверки запрещённых состояний. Создание записей сохраняет `id` в переменные Postman автоматически.

Если уже запускал коллекцию на этой же БД, результаты могут отличаться: некоторые операции не разрешено повторять. Для чистого прогона останови программу, удали `data/` и запусти снова.

## Как посмотреть SQL-таблицы

Открой <http://localhost:8080/h2-console> и введи:

- JDBC URL: `jdbc:h2:file:./data/exams`
- User Name: `EXAM_APP`
- Password: значение `DB_PASSWORD` из `.env`

Пример SQL-запроса: `SELECT * FROM exam_attempts;`

## Важно

Это **учебная версия**, сделанная для понимания кода. В ней нет регистрации, входа в аккаунт или разграничения ролей обычных пользователей приложения; в задании ограничивается именно **пользователь базы данных**. Для промышленного сервиса потребуются авторизация, миграции и защита от одновременных конфликтующих запросов.
