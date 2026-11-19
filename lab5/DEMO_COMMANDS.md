# Проверка lab5 на macOS

Открой Terminal в папке `lab5`. Команды подходят для обычного терминала macOS
(zsh или bash). Все блоки проверки выполняй по порядку в одном окне.

## 1. Подготовка и запуск

Нужна Java 17+. Для чтения токенов из JSON используется `jq`.
Если `jq` ещё нет, установи его через Homebrew:

```bash
brew install jq
```

Если переносишь проект с ПК, перенеси и скрытый файл `.env`.
Если `.env` ещё нет, эта команда создаст его со случайными паролями и ключом JWT:

```bash
if [ ! -f .env ]; then
  printf 'DB_ADMIN_PASSWORD=%s\nDB_PASSWORD=%s\nJWT_SECRET=%s\n' \
    "$(openssl rand -hex 16)" \
    "$(openssl rand -hex 16)" \
    "$(openssl rand -hex 32)" > .env
fi
```

В IntelliJ запусти `ExamApplication`, рабочая папка — `lab5`.
Дождись `Started ExamApplication` и оставь программу работающей.

## 2. Зарегистрировать пользователя

```bash
base="http://localhost:8080"
login="student$(date +%s)"
password="Student123"

curl -sS -w '\nHTTP %{http_code}\n' "$base/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$login\",\"password\":\"$password\",\"deviceId\":\"registration\"}"
```

Создаёт нового пользователя. Ожидаются два токена и `HTTP 201`.

## 3. Войти и сохранить токены

```bash
pair=$(curl -sS "$base/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$login\",\"password\":\"$password\",\"deviceId\":\"pc\"}")

printf '%s\n' "$pair" | jq .
access=$(printf '%s\n' "$pair" | jq -r '.accessToken')
refresh=$(printf '%s\n' "$pair" | jq -r '.refreshToken')
```

Ожидаются `accessToken` и `refreshToken`. `jq` достаёт их из ответа
и сохраняет в переменные `access` и `refresh`.
В БД появляется сессия устройства `pc` со статусом `ACTIVE`.

## 4. Получить экзамены с access-токеном

```bash
curl -sS -w '\nHTTP %{http_code}\n' "$base/api/exams" \
  -H "Authorization: Bearer $access"
```

Ожидаются список экзаменов и `HTTP 200`.

## 5. Обновить токены

```bash
old_access="$access"
old_refresh="$refresh"

pair=$(curl -sS "$base/auth/refresh" \
  -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$old_refresh\"}")

access=$(printf '%s\n' "$pair" | jq -r '.accessToken')
refresh=$(printf '%s\n' "$pair" | jq -r '.refreshToken')

[ "$old_access" != "$access" ] && echo 'Access изменился'
[ "$old_refresh" != "$refresh" ] && echo 'Refresh изменился'
```

Ожидаются оба сообщения. Старая сессия становится `USED`, новая — `ACTIVE`.

## 6. Повторить старый refresh

```bash
curl -sS -w '\nHTTP %{http_code}\n' "$base/auth/refresh" \
  -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$old_refresh\"}"
```

Ожидается `HTTP 401`, новая пара не выдаётся.
Старая сессия становится `REVOKED`.

## 7. Проверить доступ

С новым access-токеном список экзаменов должен открываться — `HTTP 200`:

```bash
curl -sS -w '\nHTTP %{http_code}\n' "$base/api/exams" \
  -H "Authorization: Bearer $access"
```

Без токена доступ запрещён — `HTTP 401`:

```bash
curl -sS -w '\nHTTP %{http_code}\n' "$base/api/exams"
```

## 8. Проверить сессии в БД

Останови программу кнопкой ■ в IntelliJ. Затем в том же Terminal выполни:

```bash
db_password=$(sed -n 's/^DB_PASSWORD=//p' .env | tr -d '\r')

java -cp "$HOME/.m2/repository/com/h2database/h2/2.3.232/h2-2.3.232.jar" \
  org.h2.tools.Shell \
  -url 'jdbc:h2:file:./data/exams' \
  -user EXAM_APP \
  -password "$db_password" \
  -sql "SELECT id, status FROM user_sessions WHERE username='$login' AND device_id='pc';"
```

`sed` читает пароль из `.env`, `java` запускает консоль базы H2,
а `SELECT` показывает сессии нашего входа.

Ожидаются ровно две строки: `REVOKED` и `ACTIVE`.
Сессия регистрации хранится отдельно с `device_id = 'registration'`.
