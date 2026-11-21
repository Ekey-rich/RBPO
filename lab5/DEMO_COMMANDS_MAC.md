# Показ lab5 на macOS

Нужны JDK 17+, Maven и `jq`. Если в Terminal отсутствуют `mvn` или `jq`, их можно поставить через Homebrew командой `brew install maven jq`.

## 1. Подготовить конфигурацию и запустить приложение

Открой Terminal в папке `lab5`. Если `.env` отсутствует, первый блок создаст его со случайными паролями и ключом JWT.

```bash
if [ ! -f .env ]; then
  (umask 077; printf 'DB_ADMIN_PASSWORD=%s\nDB_PASSWORD=%s\nJWT_SECRET=%s\n' \
    "$(openssl rand -hex 16)" "$(openssl rand -hex 16)" "$(openssl rand -hex 32)" > .env)
fi

mvn spring-boot:run
```

Также можно запустить `ExamApplication` в IntelliJ с рабочей папкой `lab5` после создания `.env`. При переносе существующей базы переноси папку `data` вместе с её `.env`.

## 2. Зарегистрироваться и войти

Выполняй блоки по порядку во втором окне Terminal. Регистрация должна вернуть `HTTP 201`. При входе сохраняем оба токена из ответа.

```bash
login="student$(openssl rand -hex 4)"
student_password="Student1$(openssl rand -hex 8)"

registration=$(jq -cn --arg name "$login" --arg pass "$student_password" \
  '{username:$name,password:$pass,deviceId:"registration"}')
curl -fsS -o /dev/null -w 'HTTP %{http_code}\n' 'http://127.0.0.1:8080/auth/register' \
  -H 'Content-Type: application/json' -d "$registration"

credentials=$(jq -cn --arg name "$login" --arg pass "$student_password" \
  '{username:$name,password:$pass,deviceId:"mac"}')
pair=$(curl -fsS 'http://127.0.0.1:8080/auth/login' -H 'Content-Type: application/json' -d "$credentials")
printf '%s\n' "$pair" | jq 'keys'
access=$(printf '%s\n' "$pair" | jq -er '.accessToken')
refresh=$(printf '%s\n' "$pair" | jq -er '.refreshToken')
```

`keys` показывает имена полей `accessToken` и `refreshToken`. Сами токены остаются в переменных.

## 3. Открыть защищённый список экзаменов

```bash
curl -fsS -w '\nHTTP %{http_code}\n' 'http://127.0.0.1:8080/api/exams' \
  -H "Authorization: Bearer $access"
```

Ожидается `HTTP 200`.

## 4. Обновить пару и повторно отправить старый refresh

```bash
old_access="$access"
old_refresh="$refresh"
refresh_body=$(jq -cn --arg token "$old_refresh" '{refreshToken:$token}')
pair=$(curl -fsS 'http://127.0.0.1:8080/auth/refresh' -H 'Content-Type: application/json' -d "$refresh_body")
access=$(printf '%s\n' "$pair" | jq -er '.accessToken')
refresh=$(printf '%s\n' "$pair" | jq -er '.refreshToken')

[ "$access" != "$old_access" ] && echo 'Access изменился'
[ "$refresh" != "$old_refresh" ] && echo 'Refresh изменился'

# Повторная отправка старого refresh должна вернуть 401.
curl -sS -w '\nHTTP %{http_code}\n' 'http://127.0.0.1:8080/auth/refresh' \
  -H 'Content-Type: application/json' -d "$refresh_body"
```

После первого обмена старая сессия получает `USED`, новая — `ACTIVE`. После повторной отправки старая становится `REVOKED`.

## 5. Проверить новый access

```bash
# С новым токеном ожидается HTTP 200.
curl -fsS -w '\nHTTP %{http_code}\n' 'http://127.0.0.1:8080/api/exams' \
  -H "Authorization: Bearer $access"

# Без токена ожидается HTTP 401.
curl -sS -w '\nHTTP %{http_code}\n' 'http://127.0.0.1:8080/api/exams'
```

## 6. Посмотреть сессии в H2

Останови приложение через `Ctrl+C` в первом окне Terminal или кнопкой Stop в IntelliJ. Во втором окне с запросами перейди в папку `lab5` и выполни команду.

```bash
db_password=$(sed -n 's/^DB_PASSWORD=//p' .env | tr -d '\r')
java -cp "$HOME/.m2/repository/com/h2database/h2/2.3.232/h2-2.3.232.jar" org.h2.tools.Shell \
  -url 'jdbc:h2:file:./data/exams;IFEXISTS=TRUE;ACCESS_MODE_DATA=r' \
  -user EXAM_APP -password "$db_password" \
  -sql "SELECT id, status FROM user_sessions WHERE username='$login' AND device_id='mac';"
```

Для устройства `mac` ожидаются ровно две строки, `REVOKED` и `ACTIVE`. Сессия регистрации хранится отдельно с `deviceId` `registration`.
