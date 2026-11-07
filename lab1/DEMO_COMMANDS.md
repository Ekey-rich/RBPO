# Команды для демонстрации lab1

Команды рассчитаны на PowerShell. Нужны JDK 25 и доступ к Maven Central.

## 1. Запустить сервер

Открыть PowerShell в корне `lab1` и выполнить:

```powershell
.\mvnw.cmd spring-boot:run
```

Дождаться сообщения о запуске приложения. Это окно не закрывать.

## 2. Проверить эндпоинты

Открыть **второе окно PowerShell** и выполнить по очереди:

```powershell
# GET /hello — должен вернуть Hello from Spring Boot!
Invoke-RestMethod "http://localhost:8080/hello"

# GET /sum — должен вернуть 12
Invoke-RestMethod "http://localhost:8080/sum?a=7&b=5"

# Другой пример суммы — должен вернуть 100
Invoke-RestMethod "http://localhost:8080/sum?a=40&b=60"
```

Те же адреса можно открыть в браузере:

- <http://localhost:8080/hello>
- <http://localhost:8080/sum?a=7&b=5>

## 3. Остановить сервер

В первом окне PowerShell нажать `Ctrl+C`.
