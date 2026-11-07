# Команды для демонстрации lab2

Команды рассчитаны на PowerShell. Нужны JDK 17+ и Maven.

## 1. Запустить сервер

Открыть PowerShell в корне `lab2` и выполнить:

```powershell
mvn spring-boot:run
```

Если команда `mvn` недоступна, запустить `ExamApplication.main()` из IntelliJ IDEA. Это окно запуска не закрывать.

## 2. Продемонстрировать CRUD

Открыть **второе окно PowerShell**. Команды ниже можно скопировать целиком: ID созданных записей подставляются автоматически.

```powershell
$base = "http://localhost:8080/api"

# Сначала списки пусты — данные хранятся только в памяти.
Invoke-RestMethod "$base/users"

# POST: создать пользователя.
$user = Invoke-RestMethod -Method Post -Uri "$base/users" `
    -ContentType "application/json" `
    -Body (@{ name = "Иван" } | ConvertTo-Json -Compress)
$user

# POST: создать экзамен.
$exam = Invoke-RestMethod -Method Post -Uri "$base/exams" `
    -ContentType "application/json" `
    -Body (@{ name = "Основы Java"; passingScore = 10; timeLimitMinutes = 30 } | ConvertTo-Json -Compress)
$exam

# PATCH: изменить только название экзамена.
$exam = Invoke-RestMethod -Method Patch -Uri "$base/exams/$($exam.id)" `
    -ContentType "application/json" `
    -Body (@{ name = "Основы Java — демонстрация" } | ConvertTo-Json -Compress)
$exam

# GET: получить один экзамен и список всех экзаменов.
Invoke-RestMethod "$base/exams/$($exam.id)"
Invoke-RestMethod "$base/exams"

# POST: создать задание, попытку и ответ.
$task = Invoke-RestMethod -Method Post -Uri "$base/exam-tasks" `
    -ContentType "application/json" `
    -Body (@{ examId = $exam.id; question = "2+2?"; correctAnswer = "4"; maxScore = 10 } | ConvertTo-Json -Compress)
$task

$attempt = Invoke-RestMethod -Method Post -Uri "$base/exam-attempts" `
    -ContentType "application/json" `
    -Body (@{ examId = $exam.id; userId = $user.id } | ConvertTo-Json -Compress)
$attempt

$solution = Invoke-RestMethod -Method Post -Uri "$base/task-solutions" `
    -ContentType "application/json" `
    -Body (@{ attemptId = $attempt.id; taskId = $task.id; answer = "4" } | ConvertTo-Json -Compress)
$solution

# Проверить все пять сущностей.
Invoke-RestMethod "$base/users"
Invoke-RestMethod "$base/exams"
Invoke-RestMethod "$base/exam-tasks"
Invoke-RestMethod "$base/exam-attempts"
Invoke-RestMethod "$base/task-solutions"

# DELETE: удаление экзамена также удалит связанные задания, попытки и ответы.
Invoke-RestMethod -Method Delete -Uri "$base/exams/$($exam.id)"
Invoke-RestMethod "$base/exams"
Invoke-RestMethod "$base/exam-tasks"
Invoke-RestMethod "$base/exam-attempts"
Invoke-RestMethod "$base/task-solutions"
```

После перезапуска сервера все записи очищаются, потому что используется хранилище в памяти.

## 3. Остановить сервер

В первом окне нажать `Ctrl+C`.
