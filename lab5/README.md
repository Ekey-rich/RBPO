# Лабораторная 5: JWT и сессии

Продолжение `lab4`, запускается отдельно. Экзамены, H2 и роли сохранены.

## Запуск

1. Открой папку `lab5` в IntelliJ IDEA как Maven-проект, Java 17+.
2. Запусти `ExamApplication`. Рабочая папка: `lab5`. На этом ПК `.env` уже готов.
3. Импортируй `postman/exams-lab5.postman_collection.json` и запускай запросы по порядку.

На другом ПК скопируй `.env.example` в `.env`, задай пароли БД и случайный
`JWT_SECRET` от 32 байт. База создаётся в `data/exams.mv.db`.

## Запросы

- `POST /auth/register`: `{"username":"student","password":"Student123"}` → 201.
- `POST /auth/login`: тот же JSON → 200.
- `POST /auth/refresh`: `{"refreshToken":"..."}` → 200.

В ответах два поля: `accessToken` и `refreshToken`. Для `/api/exams` передавай
`Authorization: Bearer <accessToken>`. Refresh отправляется только в JSON.
Время жизни токенов задано в `application.properties`.

## Проверка сессий

Postman сохраняет токены автоматически. После входа сессия ACTIVE;
после обновления старая USED, новая ACTIVE; после повтора старого refresh
ответ 401, старая REVOKED, новая ACTIVE. Третья строка не появляется.

Для просмотра БД останови приложение и подключись к H2 в IDEA:
`jdbc:h2:file:ПОЛНЫЙ_ПУТЬ/lab5/data/exams`, пользователь `sa`, пароль `DB_ADMIN_PASSWORD` из `.env`.
Подставь логин из Postman:

```sql
SELECT id, status FROM user_sessions
WHERE username = 'student_...' AND device_id = 'postman';
```

Регистрация создаёт отдельную сессию с `device_id = 'registration'`.
