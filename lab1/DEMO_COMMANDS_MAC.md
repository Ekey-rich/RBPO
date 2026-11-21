# Показ lab1 на macOS

Нужен JDK 25. Открой Terminal в папке `lab1` и запусти приложение.

```bash
bash ./mvnw spring-boot:run
```

Дождись `Started Lab1Application`. Во втором окне Terminal выполни запросы по очереди.

```bash
# Приветствие. Ответ: Hello from Spring Boot!
curl -fsS 'http://127.0.0.1:8080/hello'; printf '\n'

# Сложение. Ответы: 12 и 100.
curl -fsS 'http://127.0.0.1:8080/sum?a=7&b=5'; printf '\n'
curl -fsS 'http://127.0.0.1:8080/sum?a=40&b=60'; printf '\n'
```

Эти адреса можно открыть и в браузере. Чтобы остановить приложение, нажми `Ctrl+C` в первом окне.
