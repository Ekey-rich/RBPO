# Показ lab2 на macOS

Нужны JDK 17+, Maven и `jq`. Если в Terminal отсутствуют `mvn` или `jq`, их можно поставить через Homebrew командой `brew install maven jq`.

## 1. Запустить приложение

Открой Terminal в папке `lab2`.

```bash
mvn spring-boot:run
```

Также можно запустить `ExamApplication` из IntelliJ. Оставь приложение запущенным.

## 2. Показать создание, чтение, изменение и удаление

Выполняй команды по порядку во втором окне Terminal. `jq` достаёт ID из ответа сервера.

```bash
# После свежего запуска список пуст.
curl -fsS 'http://127.0.0.1:8080/api/users' | jq .

# Создать пользователя и экзамен.
user=$(printf '%s' '{"name":"Иван"}' | curl -fsS 'http://127.0.0.1:8080/api/users' \
  -H 'Content-Type: application/json; charset=utf-8' --data-binary @-)
printf '%s\n' "$user" | jq .
user_id=$(printf '%s\n' "$user" | jq -er '.id')

exam=$(printf '%s' '{"name":"Основы Java","passingScore":10,"timeLimitMinutes":30}' | \
  curl -fsS 'http://127.0.0.1:8080/api/exams' \
    -H 'Content-Type: application/json; charset=utf-8' --data-binary @-)
printf '%s\n' "$exam" | jq .
exam_id=$(printf '%s\n' "$exam" | jq -er '.id')

# Поменять и проверить название экзамена.
printf '%s' '{"name":"Основы Java — демонстрация"}' | \
  curl -fsS -X PATCH "http://127.0.0.1:8080/api/exams/$exam_id" \
    -H 'Content-Type: application/json; charset=utf-8' --data-binary @- | jq .
curl -fsS "http://127.0.0.1:8080/api/exams/$exam_id" | jq .

# Добавить задание, попытку и ответ.
task=$(curl -fsS 'http://127.0.0.1:8080/api/exam-tasks' \
  -H 'Content-Type: application/json' \
  -d "{\"examId\":$exam_id,\"question\":\"2+2?\",\"correctAnswer\":\"4\",\"maxScore\":10}")
printf '%s\n' "$task" | jq .
task_id=$(printf '%s\n' "$task" | jq -er '.id')

attempt=$(curl -fsS 'http://127.0.0.1:8080/api/exam-attempts' \
  -H 'Content-Type: application/json' -d "{\"examId\":$exam_id,\"userId\":$user_id}")
printf '%s\n' "$attempt" | jq .
attempt_id=$(printf '%s\n' "$attempt" | jq -er '.id')

curl -fsS 'http://127.0.0.1:8080/api/task-solutions' \
  -H 'Content-Type: application/json' \
  -d "{\"attemptId\":$attempt_id,\"taskId\":$task_id,\"answer\":\"4\"}" | jq .

# Посмотреть все пять списков.
curl -fsS 'http://127.0.0.1:8080/api/users' | jq .
curl -fsS 'http://127.0.0.1:8080/api/exams' | jq .
curl -fsS 'http://127.0.0.1:8080/api/exam-tasks' | jq .
curl -fsS 'http://127.0.0.1:8080/api/exam-attempts' | jq .
curl -fsS 'http://127.0.0.1:8080/api/task-solutions' | jq .

# Удалить экзамен. Ожидается 204, связанные записи тоже удалятся.
curl -fsS -X DELETE -o /dev/null -w 'HTTP %{http_code}\n' "http://127.0.0.1:8080/api/exams/$exam_id"
curl -fsS 'http://127.0.0.1:8080/api/exams' | jq .
curl -fsS 'http://127.0.0.1:8080/api/exam-tasks' | jq .
curl -fsS 'http://127.0.0.1:8080/api/exam-attempts' | jq .
curl -fsS 'http://127.0.0.1:8080/api/task-solutions' | jq .
```

Останови приложение через `Ctrl+C` в первом окне. Данные хранятся в памяти и очищаются при перезапуске.
