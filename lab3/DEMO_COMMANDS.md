# Команды для демонстрации lab3

Команды рассчитаны на PowerShell. Нужны JDK 17+ и Maven.

## 1. Подготовить конфигурацию

Один раз в корне `lab3` создать `.env` и заменить в нём оба пароля:

```powershell
Copy-Item .env.example .env
notepad .env
```

Файл `.env` не добавлять в Git.

## 2. Запустить сервер

В корне `lab3` выполнить:

```powershell
mvn spring-boot:run
```

Если команда `mvn` недоступна, запустить `ExamApplication.main()` из IntelliJ IDEA с рабочей папкой `lab3`. Окно запуска не закрывать.

## 3. Проверить исходные данные

Во **втором окне PowerShell**:

```powershell
$base = "http://localhost:8080/api"

Invoke-RestMethod "$base/users"
Invoke-RestMethod "$base/exams"
Invoke-RestMethod "$base/exam-tasks"
Invoke-RestMethod "$base/exam-attempts"
Invoke-RestMethod "$base/task-solutions"
```

## 4. Показать CRUD и все пять бизнес-операций

Скопировать блок целиком. Создаются новые записи, поэтому повторный запуск не зависит от фиксированных ID.

```powershell
$base = "http://localhost:8080/api"

# CRUD: создать пользователя и черновик экзамена.
$user = Invoke-RestMethod -Method Post -Uri "$base/users" `
    -ContentType "application/json" `
    -Body (@{ name = "Демо Студент" } | ConvertTo-Json -Compress)
$user

$exam = Invoke-RestMethod -Method Post -Uri "$base/exams" `
    -ContentType "application/json" `
    -Body (@{ name = "Демо-экзамен"; passingScore = 10; timeLimitMinutes = 30 } | ConvertTo-Json -Compress)
$exam

# CRUD: изменить черновик через PATCH.
$exam = Invoke-RestMethod -Method Patch -Uri "$base/exams/$($exam.id)" `
    -ContentType "application/json" `
    -Body (@{ name = "Демо-экзамен по Java" } | ConvertTo-Json -Compress)
$exam

# CRUD: добавить задание.
$task = Invoke-RestMethod -Method Post -Uri "$base/exam-tasks" `
    -ContentType "application/json" `
    -Body (@{ examId = $exam.id; question = "2+2?"; correctAnswer = "4"; maxScore = 10 } | ConvertTo-Json -Compress)
$task

# Бизнес-операция 1: опубликовать экзамен.
$exam = Invoke-RestMethod -Method Post -Uri "$base/operations/publish/$($exam.id)"
$exam

# Бизнес-операция 2: начать попытку.
$attempt = Invoke-RestMethod -Method Post -Uri "$base/operations/start-attempt" `
    -ContentType "application/json" `
    -Body (@{ examId = $exam.id; userId = $user.id } | ConvertTo-Json -Compress)
$attempt

# Бизнес-операция 3: сохранить правильный ответ; points и totalScore станут 10.
$solution = Invoke-RestMethod -Method Post -Uri "$base/operations/answer" `
    -ContentType "application/json" `
    -Body (@{ attemptId = $attempt.id; taskId = $task.id; answer = "4" } | ConvertTo-Json -Compress)
$solution
Invoke-RestMethod "$base/exam-attempts/$($attempt.id)"

# Бизнес-операция 4: завершить попытку; completedExams пользователя увеличится на 1.
$attempt = Invoke-RestMethod -Method Post -Uri "$base/operations/finish/$($attempt.id)"
$attempt
Invoke-RestMethod "$base/users/$($user.id)"

# Бизнес-операция 5: архивировать экзамен после завершения попытки.
$exam = Invoke-RestMethod -Method Post -Uri "$base/operations/archive/$($exam.id)"
$exam
```

## 5. Дополнительно показать базу H2

Открыть <http://localhost:8080/h2-console>:

- JDBC URL: `jdbc:h2:file:./data/exams`
- User Name: `EXAM_APP`
- Password: значение `DB_PASSWORD` из `.env`

Пример SQL-запроса:

```sql
SELECT * FROM exam_attempts;
```

Данные сохраняются в `data/exams.mv.db` после перезапуска. Для полностью чистой демонстрации нужно остановить сервер, удалить папку `data` и снова запустить проект.

## 6. Остановить сервер

В первом окне нажать `Ctrl+C`.
