# Показ lab4 на macOS

Нужны JDK 17+, Maven и `jq`. Если в Terminal отсутствуют `mvn` или `jq`, их можно поставить через Homebrew командой `brew install maven jq`.

## 1. Запустить приложение с администратором

Открой Terminal в папке `lab4`. При первом запуске задай пароль для `admin`. Если аккаунт уже существует, используй пароль, с которым его создали.

```bash
if [ ! -f .env ]; then
  (umask 077; printf 'DB_ADMIN_PASSWORD=%s\nDB_PASSWORD=%s\n' \
    "$(openssl rand -hex 16)" "$(openssl rand -hex 16)" > .env)
fi

printf 'Пароль admin: '
read -rs ADMIN_PASSWORD
printf '\n'
export ADMIN_PASSWORD
mvn spring-boot:run
```

Для запуска из IntelliJ укажи `ADMIN_PASSWORD` в конфигурации `ExamApplication` и выбери `lab4` рабочей папкой. При переносе существующей базы переноси папку `data` вместе с её `.env`.

## 2. Зарегистрировать студента

Дальше выполняй блоки по порядку во втором окне Terminal. У каждого пользователя отдельный файл с cookie. CSRF-токен берётся через `/csrf` и отправляется вместе с той же cookie.

```bash
student_login="student$(openssl rand -hex 4)"
student_password="Student1$(openssl rand -hex 8)"
session_dir=$(mktemp -d "${TMPDIR:-/tmp}/lab4.XXXXXX")

student_csrf=$(curl -fsS -c "$session_dir/student.cookies" 'http://127.0.0.1:8080/csrf' | jq -er '.token')
registration=$(jq -cn --arg name "$student_login" --arg pass "$student_password" '{username:$name,password:$pass}')
curl -fsS -b "$session_dir/student.cookies" 'http://127.0.0.1:8080/register' \
  -H "X-CSRF-TOKEN: $student_csrf" -H 'Content-Type: application/json' -d "$registration"
printf '\n'

# Студент видит свою карточку и задания без правильных ответов.
student=$(curl -fsS -u "$student_login:$student_password" 'http://127.0.0.1:8080/api/users')
printf '%s\n' "$student" | jq .
student_id=$(printf '%s\n' "$student" | jq -er '.[0].id')
curl -fsS -u "$student_login:$student_password" 'http://127.0.0.1:8080/api/exam-tasks' | jq .
```

## 3. Создать и опубликовать экзамен от имени admin

```bash
printf 'Пароль admin: '
read -rs admin_password
printf '\n'
admin_csrf=$(curl -fsS -c "$session_dir/admin.cookies" 'http://127.0.0.1:8080/csrf' | jq -er '.token')

exam=$(printf '%s' '{"name":"Демо Spring Security","passingScore":10,"timeLimitMinutes":30}' | \
  curl -fsS -u "admin:$admin_password" -b "$session_dir/admin.cookies" 'http://127.0.0.1:8080/api/exams' \
    -H "X-CSRF-TOKEN: $admin_csrf" -H 'Content-Type: application/json; charset=utf-8' --data-binary @-)
printf '%s\n' "$exam" | jq .
exam_id=$(printf '%s\n' "$exam" | jq -er '.id')

task=$(curl -fsS -u "admin:$admin_password" -b "$session_dir/admin.cookies" 'http://127.0.0.1:8080/api/exam-tasks' \
  -H "X-CSRF-TOKEN: $admin_csrf" -H 'Content-Type: application/json' \
  -d "{\"examId\":$exam_id,\"question\":\"2+2?\",\"correctAnswer\":\"4\",\"maxScore\":10}")
printf '%s\n' "$task" | jq .
task_id=$(printf '%s\n' "$task" | jq -er '.id')

curl -fsS -X POST -u "admin:$admin_password" -b "$session_dir/admin.cookies" \
  -H "X-CSRF-TOKEN: $admin_csrf" "http://127.0.0.1:8080/api/operations/publish/$exam_id" | jq .

# Админ видит правильные ответы.
curl -fsS -u "admin:$admin_password" 'http://127.0.0.1:8080/api/exam-tasks' | jq .
```

## 4. Пройти экзамен от имени студента

```bash
attempt=$(curl -fsS -u "$student_login:$student_password" -b "$session_dir/student.cookies" \
  'http://127.0.0.1:8080/api/operations/start-attempt' \
  -H "X-CSRF-TOKEN: $student_csrf" -H 'Content-Type: application/json' \
  -d "{\"examId\":$exam_id,\"userId\":$student_id}")
printf '%s\n' "$attempt" | jq .
attempt_id=$(printf '%s\n' "$attempt" | jq -er '.id')

# Ответить на задание. Ожидается points = 10.
curl -fsS -u "$student_login:$student_password" -b "$session_dir/student.cookies" \
  'http://127.0.0.1:8080/api/operations/answer' \
  -H "X-CSRF-TOKEN: $student_csrf" -H 'Content-Type: application/json' \
  -d "{\"attemptId\":$attempt_id,\"taskId\":$task_id,\"answer\":\"4\"}" | jq .

# Завершить попытку и посмотреть её статус и баллы.
curl -fsS -X POST -u "$student_login:$student_password" -b "$session_dir/student.cookies" \
  -H "X-CSRF-TOKEN: $student_csrf" "http://127.0.0.1:8080/api/operations/finish/$attempt_id" | jq .
curl -fsS -u "$student_login:$student_password" 'http://127.0.0.1:8080/api/exam-attempts' | jq .

# Админ архивирует экзамен.
curl -fsS -X POST -u "admin:$admin_password" -b "$session_dir/admin.cookies" \
  -H "X-CSRF-TOKEN: $admin_csrf" "http://127.0.0.1:8080/api/operations/archive/$exam_id" | jq .
```

## 5. Показать ограничения доступа

```bash
# 401: запрос без входа.
curl -sS -o /dev/null -w 'HTTP %{http_code}\n' 'http://127.0.0.1:8080/api/exams'

# 403: студент пытается создать экзамен.
printf '%s' '{"name":"Запрещённый экзамен","passingScore":5,"timeLimitMinutes":20}' | \
  curl -sS -o /dev/null -w 'HTTP %{http_code}\n' \
    -u "$student_login:$student_password" -b "$session_dir/student.cookies" 'http://127.0.0.1:8080/api/exams' \
    -H "X-CSRF-TOKEN: $student_csrf" -H 'Content-Type: application/json; charset=utf-8' --data-binary @-

# 403: студент пытается начать попытку за пользователя №1.
curl -sS -o /dev/null -w 'HTTP %{http_code}\n' \
  -u "$student_login:$student_password" -b "$session_dir/student.cookies" 'http://127.0.0.1:8080/api/operations/start-attempt' \
  -H "X-CSRF-TOKEN: $student_csrf" -H 'Content-Type: application/json' \
  -d "{\"examId\":$exam_id,\"userId\":1}"
```

## 6. Остановить приложение и посмотреть таблицы

Нажми `Ctrl+C` в первом окне. В этом же окне, из папки `lab4`, можно посмотреть записи в H2.

```bash
db_password=$(sed -n 's/^DB_PASSWORD=//p' .env | tr -d '\r')
java -cp "$HOME/.m2/repository/com/h2database/h2/2.3.232/h2-2.3.232.jar" org.h2.tools.Shell \
  -url 'jdbc:h2:file:./data/exams;IFEXISTS=TRUE;ACCESS_MODE_DATA=r' \
  -user EXAM_APP -password "$db_password" \
  -sql 'SHOW TABLES; SELECT id, username, role FROM users; SELECT * FROM exam_attempts;'
```

В окне с запросами удали временные файлы cookie после показа.

```bash
rm -f "$session_dir/student.cookies" "$session_dir/admin.cookies"
rmdir "$session_dir"
```
