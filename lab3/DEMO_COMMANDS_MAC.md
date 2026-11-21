# Показ lab3 на macOS

Нужны JDK 17+, Maven и `jq`. Если в Terminal отсутствуют `mvn` или `jq`, их можно поставить через Homebrew командой `brew install maven jq`.

## 1. Подготовить базу и запустить приложение

Открой Terminal в папке `lab3`. Если `.env` отсутствует, первый блок создаст его со случайными паролями для H2.

```bash
if [ ! -f .env ]; then
  (umask 077; printf 'DB_ADMIN_PASSWORD=%s\nDB_PASSWORD=%s\n' \
    "$(openssl rand -hex 16)" "$(openssl rand -hex 16)" > .env)
fi

mvn spring-boot:run
```

Также можно запустить `ExamApplication` в IntelliJ с рабочей папкой `lab3` после создания `.env`. При переносе существующей базы переноси папку `data` вместе с её `.env`.

## 2. Посмотреть исходные записи

Выполняй команды проверки по порядку во втором окне Terminal.

```bash
curl -fsS 'http://127.0.0.1:8080/api/users' | jq .
curl -fsS 'http://127.0.0.1:8080/api/exams' | jq .
curl -fsS 'http://127.0.0.1:8080/api/exam-tasks' | jq .
curl -fsS 'http://127.0.0.1:8080/api/exam-attempts' | jq .
curl -fsS 'http://127.0.0.1:8080/api/task-solutions' | jq .
```

## 3. Создать пользователя, черновик и задание

```bash
user=$(printf '%s' '{"name":"Демо Студент"}' | curl -fsS 'http://127.0.0.1:8080/api/users' \
  -H 'Content-Type: application/json; charset=utf-8' --data-binary @-)
printf '%s\n' "$user" | jq .
user_id=$(printf '%s\n' "$user" | jq -er '.id')

exam=$(printf '%s' '{"name":"Демо-экзамен","passingScore":10,"timeLimitMinutes":30}' | \
  curl -fsS 'http://127.0.0.1:8080/api/exams' \
    -H 'Content-Type: application/json; charset=utf-8' --data-binary @-)
printf '%s\n' "$exam" | jq .
exam_id=$(printf '%s\n' "$exam" | jq -er '.id')

# Изменить название черновика.
printf '%s' '{"name":"Демо-экзамен по Java"}' | \
  curl -fsS -X PATCH "http://127.0.0.1:8080/api/exams/$exam_id" \
    -H 'Content-Type: application/json; charset=utf-8' --data-binary @- | jq .

task=$(curl -fsS 'http://127.0.0.1:8080/api/exam-tasks' \
  -H 'Content-Type: application/json' \
  -d "{\"examId\":$exam_id,\"question\":\"2+2?\",\"correctAnswer\":\"4\",\"maxScore\":10}")
printf '%s\n' "$task" | jq .
task_id=$(printf '%s\n' "$task" | jq -er '.id')
```

## 4. Показать пять бизнес-операций

```bash
# 1. Опубликовать экзамен.
curl -fsS -X POST "http://127.0.0.1:8080/api/operations/publish/$exam_id" | jq .

# 2. Начать попытку.
attempt=$(curl -fsS 'http://127.0.0.1:8080/api/operations/start-attempt' \
  -H 'Content-Type: application/json' -d "{\"examId\":$exam_id,\"userId\":$user_id}")
printf '%s\n' "$attempt" | jq .
attempt_id=$(printf '%s\n' "$attempt" | jq -er '.id')

# 3. Отправить ответ и увидеть общий балл 10.
curl -fsS 'http://127.0.0.1:8080/api/operations/answer' \
  -H 'Content-Type: application/json' \
  -d "{\"attemptId\":$attempt_id,\"taskId\":$task_id,\"answer\":\"4\"}" | jq .
curl -fsS "http://127.0.0.1:8080/api/exam-attempts/$attempt_id" | jq .

# 4. Завершить попытку и посмотреть счётчик completedExams студента.
curl -fsS -X POST "http://127.0.0.1:8080/api/operations/finish/$attempt_id" | jq .
curl -fsS "http://127.0.0.1:8080/api/users/$user_id" | jq .

# 5. Архивировать экзамен.
curl -fsS -X POST "http://127.0.0.1:8080/api/operations/archive/$exam_id" | jq .
```

## 5. Показать удаление

Создай отдельного пользователя без попыток и удали его. Ожидается `HTTP 204`.

```bash
temporary_user=$(printf '%s' '{"name":"Временный студент"}' | \
  curl -fsS 'http://127.0.0.1:8080/api/users' \
    -H 'Content-Type: application/json; charset=utf-8' --data-binary @-)
temporary_id=$(printf '%s\n' "$temporary_user" | jq -er '.id')
curl -fsS -X DELETE -o /dev/null -w 'HTTP %{http_code}\n' "http://127.0.0.1:8080/api/users/$temporary_id"
```

## 6. Показать таблицы H2

Открой <http://127.0.0.1:8080/h2-console> при работающем приложении. JDBC URL — `jdbc:h2:file:./data/exams`, User Name — `EXAM_APP`, Password — значение `DB_PASSWORD` из `.env`.

```sql
SELECT * FROM exam_attempts;
```

Чтобы остановить приложение, нажми `Ctrl+C` в первом окне. Записи сохранятся в `data/exams.mv.db`.
