# Команды для демонстрации lab4

Команды рассчитаны на PowerShell. Нужны JDK 17+ и Maven.

## 1. Подготовить конфигурацию

Один раз в корне `lab4` создать `.env` и заменить в нём оба пароля базы данных:

```powershell
Copy-Item .env.example .env
notepad .env
```

Файл `.env` не добавлять в Git.

## 2. Запустить сервер и создать администратора

В корне `lab4` выполнить:

```powershell
$env:ADMIN_PASSWORD = "Admin12345!"
mvn spring-boot:run
```

Если команда `mvn` недоступна, задать `ADMIN_PASSWORD` в конфигурации запуска IntelliJ IDEA и запустить `ExamApplication.main()` с рабочей папкой `lab4`.

Администратор `admin` создаётся только при первом запуске пустой базы. Если он уже существует, смена `ADMIN_PASSWORD` не меняет его старый пароль.

## 3. Зарегистрировать обычного пользователя

Открыть **второе окно PowerShell** и выполнить блок целиком:

```powershell
$base = "http://localhost:8080"
$login = "student$(Get-Random -Minimum 1000 -Maximum 99999)"
$userPassword = "Student12345"
$adminPassword = "Admin12345!"

# Получить CSRF-токен и cookie сессии.
$csrf = Invoke-RestMethod -Uri "$base/csrf" -SessionVariable userSession
$userCsrf = $csrf.token

# Регистрация доступна без логина, но требует CSRF-токен.
Invoke-RestMethod -Method Post -Uri "$base/register" `
    -WebSession $userSession `
    -Headers @{ "X-CSRF-TOKEN" = $userCsrf } `
    -ContentType "application/json" `
    -Body (@{ username = $login; password = $userPassword } | ConvertTo-Json -Compress)

# Basic Auth для последующих запросов пользователя.
$userAuth = "Basic " + [Convert]::ToBase64String(
    [Text.Encoding]::ASCII.GetBytes("${login}:${userPassword}")
)
$userHeaders = @{ Authorization = $userAuth; "X-CSRF-TOKEN" = $userCsrf }

# USER видит только свою карточку; пароль в ответе отсутствует.
$me = Invoke-RestMethod -Uri "$base/api/users" -WebSession $userSession -Headers $userHeaders
$me
$userId = @($me)[0].id

# USER видит задания, но поле correctAnswer скрыто.
Invoke-RestMethod -Uri "$base/api/exam-tasks" -WebSession $userSession -Headers $userHeaders
```

## 4. Создать и опубликовать экзамен от имени ADMIN

Продолжить в том же втором окне PowerShell:

```powershell
# Для ADMIN используем отдельную сессию и отдельный CSRF-токен.
$adminCsrfResponse = Invoke-RestMethod -Uri "$base/csrf" -SessionVariable adminSession
$adminCsrf = $adminCsrfResponse.token
$adminAuth = "Basic " + [Convert]::ToBase64String(
    [Text.Encoding]::ASCII.GetBytes("admin:${adminPassword}")
)
$adminHeaders = @{ Authorization = $adminAuth; "X-CSRF-TOKEN" = $adminCsrf }

# Создание экзамена разрешено только ADMIN.
$exam = Invoke-RestMethod -Method Post -Uri "$base/api/exams" `
    -WebSession $adminSession -Headers $adminHeaders `
    -ContentType "application/json" `
    -Body (@{ name = "Демо Spring Security"; passingScore = 10; timeLimitMinutes = 30 } | ConvertTo-Json -Compress)
$exam

$task = Invoke-RestMethod -Method Post -Uri "$base/api/exam-tasks" `
    -WebSession $adminSession -Headers $adminHeaders `
    -ContentType "application/json" `
    -Body (@{ examId = $exam.id; question = "2+2?"; correctAnswer = "4"; maxScore = 10 } | ConvertTo-Json -Compress)
$task

# Публикация также разрешена только ADMIN.
$exam = Invoke-RestMethod -Method Post -Uri "$base/api/operations/publish/$($exam.id)" `
    -WebSession $adminSession -Headers $adminHeaders
$exam

# ADMIN видит правильные ответы.
Invoke-RestMethod -Uri "$base/api/exam-tasks" -WebSession $adminSession -Headers $adminHeaders
```

## 5. Пройти экзамен от имени USER

Продолжить в том же окне. ID пользователя, экзамена, задания и попытки подставляются автоматически:

```powershell
# USER может начать попытку только со своим userId.
$attempt = Invoke-RestMethod -Method Post -Uri "$base/api/operations/start-attempt" `
    -WebSession $userSession -Headers $userHeaders `
    -ContentType "application/json" `
    -Body (@{ examId = $exam.id; userId = $userId } | ConvertTo-Json -Compress)
$attempt

# Сохранить правильный ответ и получить 10 баллов.
$solution = Invoke-RestMethod -Method Post -Uri "$base/api/operations/answer" `
    -WebSession $userSession -Headers $userHeaders `
    -ContentType "application/json" `
    -Body (@{ attemptId = $attempt.id; taskId = $task.id; answer = "4" } | ConvertTo-Json -Compress)
$solution

# Завершить собственную попытку.
$attempt = Invoke-RestMethod -Method Post -Uri "$base/api/operations/finish/$($attempt.id)" `
    -WebSession $userSession -Headers $userHeaders
$attempt

# Проверить, что USER видит свою завершённую попытку.
Invoke-RestMethod -Uri "$base/api/exam-attempts" -WebSession $userSession -Headers $userHeaders

# Архивировать экзамен может только ADMIN.
$exam = Invoke-RestMethod -Method Post -Uri "$base/api/operations/archive/$($exam.id)" `
    -WebSession $adminSession -Headers $adminHeaders
$exam
```

## 6. Показать ограничения безопасности

Эти команды специально вызывают ошибки `401` и `403`:

```powershell
# 401: запрос защищённого ресурса без Basic Auth.
try {
    Invoke-RestMethod "$base/api/exams"
} catch {
    $_.Exception.Response.StatusCode.value__
}

# 403: USER не имеет права создавать экзамен даже с правильным CSRF-токеном.
try {
    Invoke-RestMethod -Method Post -Uri "$base/api/exams" `
        -WebSession $userSession -Headers $userHeaders `
        -ContentType "application/json" `
        -Body (@{ name = "Запрещённый экзамен"; passingScore = 5; timeLimitMinutes = 20 } | ConvertTo-Json -Compress)
} catch {
    $_.Exception.Response.StatusCode.value__
}

# 403: USER пытается начать экзамен за другого пользователя.
try {
    Invoke-RestMethod -Method Post -Uri "$base/api/operations/start-attempt" `
        -WebSession $userSession -Headers $userHeaders `
        -ContentType "application/json" `
        -Body (@{ examId = $exam.id; userId = 1 } | ConvertTo-Json -Compress)
} catch {
    $_.Exception.Response.StatusCode.value__
}
```

## 7. Остановить сервер

В первом окне нажать `Ctrl+C`. Данные сохраняются в папке `data`; для чистого повторного сценария нужно остановить сервер и удалить эту папку. После удаления базы администратор будет создан заново при следующем запуске.
