# Лабораторная 4 — Spring Security (вариант 25)

Продолжение лабораторной 3: **H2, CRUD и все 5 бизнес-операций сохранены**.

Добавлено только 2 Java-класса: `SecurityConfig`, `AuthController`. В старых классах добавлены проверки доступа.

## Запуск

Нужны Java 17+ и Maven.

1. Скопировать `.env.example` в `.env`; **заменить** `DB_ADMIN_PASSWORD` и `DB_PASSWORD` на свои пароли. Не коммитить `.env`.
2. В PowerShell в папке `lab4` задать пароль администратора API:

   ```powershell
   $env:ADMIN_PASSWORD="Admin12345!"
   mvn spring-boot:run
   ```

   Администратор `admin` создаётся **один раз** на первом старте, если задана переменная `ADMIN_PASSWORD`. Если потом изменить эту переменную, старый пароль в базе останется прежним.
3. Импортировать `postman/exams-lab4.postman_collection.json` в Postman. Установить значения переменных коллекции `baseUrl`, `adminPassword` (значение из п.2).
4. Вызывать по порядку: `GET CSRF` → `POST Register` → `GET Login` → `GET Exams` → `POST Start attempt` и другие. Postman сохраняет cookie JSESSIONID и токен из `GET CSRF`. После смены пользователя повторить `GET CSRF` при необходимости.

**Важно:** папка `data/` намеренно не включена. Проект создаёт свою H2-базу при первом запуске. Так не нужно знать пароль старой готовой БД. Если хотите перенести старую БД из `lab3`, скопируйте `data/` вручную, используйте её **исходный** `DB_ADMIN_PASSWORD`; таблица `users` расширится при запуске.

## Эндпоинты

- `GET /csrf` — получить CSRF-токен (без логина; вместе с cookie сессии).
- `POST /register` — создать обычного пользователя, JSON `{"username":"ivan", "password":"Test12345"}`; роль всегда `USER`.
- `GET /api/exams`, `GET /api/exam-tasks` — просмотр для авторизованных; `correctAnswer` скрыт для USER.
- `GET /api/users`, `/api/exam-attempts`, `/api/task-solutions` — USER видит только свои записи; пароли в ответах скрыты.
- `POST`, `PATCH`, `DELETE /api/{type}` — только ADMIN.
- `POST /api/operations/publish/{examId}`, `POST /api/operations/archive/{examId}` — только ADMIN.
- `POST /api/operations/start-attempt` — USER/ADMIN, JSON `{"examId":2,"userId":3}`; обычный USER может указывать **только собственный** ID.
- `POST /api/operations/answer` — USER/ADMIN, JSON `{"attemptId":3,"taskId":3,"answer":"SELECT"}`; USER только по своей попытке.
- `POST /api/operations/finish/{attemptId}` — USER/ADMIN, только своя попытка.

Для всех POST/PATCH/DELETE передавать `X-CSRF-TOKEN` и cookie сессии, полученные через `/csrf`. HTTP Basic передаёт логин и пароль. Токен **не заменяет** Basic Auth.

## Ошибки

- `401` — нет правильных учётных данных в Basic Auth;
- `403` — недостаточно прав, чужая попытка или отсутствует CSRF-токен;
- `400` — слабый пароль, неправильные входные данные или запрет бизнес-логики;
- `409` — нарушение ограничений связанной БД.

Роли `USER` и `ADMIN` — роли **веб-приложения**. Пользователь базы `EXAM_APP` — отдельный технический аккаунт, его ограничения из лабы 3 сохранены.

### Примечания

Пользователи `Иван` и `Мария`, которые создаются из `data.sql`, — тестовые записи **без логина и пароля**, поэтому они сами по себе не могут войти. Регистрация создаёт нового пользователя с учётными данными. Для операций `start-attempt` используйте ID нового пользователя из GET `/api/users` под его Basic-авторизацией.

`ADMIN_PASSWORD` нужен при первом запуске. В учебном примере локальный HTTP используется только для localhost; при развёртывании по сети нужен HTTPS.
