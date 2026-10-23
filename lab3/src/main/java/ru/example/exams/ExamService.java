package ru.example.exams;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Transactional // Если возникнет ошибка, изменения нескольких таблиц отменяются вместе.
public class ExamService {
    private final JdbcTemplate db;

    public ExamService(JdbcTemplate db) {
        this.db = db;
    }

    // ---------- Обычный CRUD ----------

    public List<Map<String, Object>> all(String type) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : db.queryForList("SELECT * FROM " + table(type) + " ORDER BY id")) {
            result.add(camel(row));
        }
        return result;
    }

    public Map<String, Object> one(String type, long id) {
        List<Map<String, Object>> rows = db.queryForList("SELECT * FROM " + table(type) + " WHERE id = ?", id);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Запись не найдена");
        }
        return camel(rows.get(0));
    }

    public Map<String, Object> create(String type, Map<String, Object> body) {
        switch (type) {
            case "users" -> {
                only(body, "name");
                text(body, "name");
            }
            case "exams" -> {
                only(body, "name", "passingScore", "timeLimitMinutes");
                text(body, "name");
                number(body, "passingScore", 0);
                number(body, "timeLimitMinutes", 1);
            }
            case "exam-tasks" -> {
                only(body, "examId", "question", "correctAnswer", "maxScore");
                draft(number(body, "examId", 1));
                text(body, "question");
                text(body, "correctAnswer");
                number(body, "maxScore", 1);
            }
            case "exam-attempts" -> {
                only(body, "examId", "userId");
                return startAttempt(number(body, "examId", 1), number(body, "userId", 1));
            }
            case "task-solutions" -> {
                only(body, "attemptId", "taskId", "answer");
                return answer(number(body, "attemptId", 1), number(body, "taskId", 1), text(body, "answer"));
            }
            default -> table(type);
        }
        return insert(type, body);
    }

    public Map<String, Object> change(String type, long id, Map<String, Object> body) {
        Map<String, Object> item = one(type, id);
        check(!body.isEmpty(), "Укажите поля для изменения");

        switch (type) {
            case "users" -> {
                only(body, "name");
                if (body.containsKey("name")) text(body, "name");
            }
            case "exams" -> {
                draft(id);
                only(body, "name", "passingScore", "timeLimitMinutes");
                if (body.containsKey("name")) text(body, "name");
                if (body.containsKey("passingScore")) number(body, "passingScore", 0);
                if (body.containsKey("timeLimitMinutes")) number(body, "timeLimitMinutes", 1);
            }
            case "exam-tasks" -> {
                draft(((Number) item.get("examId")).longValue());
                only(body, "question", "correctAnswer", "maxScore");
                if (body.containsKey("question")) text(body, "question");
                if (body.containsKey("correctAnswer")) text(body, "correctAnswer");
                if (body.containsKey("maxScore")) number(body, "maxScore", 1);
            }
            case "exam-attempts" -> {
                only(body, "status");
                check("COMPLETED".equals(body.get("status")), "Разрешено только завершение попытки");
                return finish(id);
            }
            case "task-solutions" -> {
                only(body, "answer");
                return changeAnswer(id, text(body, "answer"));
            }
        }
        return patch(type, id, body);
    }

    public void delete(String type, long id) {
        Map<String, Object> item = one(type, id);
        if (type.equals("exams")) draft(id);
        if (type.equals("exam-tasks")) draft(((Number) item.get("examId")).longValue());
        if (type.equals("exam-attempts")) {
            check(item.get("status").equals("IN_PROGRESS"), "Нельзя удалить завершённую попытку");
            check(count("SELECT COUNT(*) FROM task_solutions WHERE attempt_id=?", id) == 0,
                    "Сначала удалите ответы");
        }
        if (type.equals("task-solutions")) {
            long attemptId = ((Number) item.get("attemptId")).longValue();
            check(one("exam-attempts", attemptId).get("status").equals("IN_PROGRESS"),
                    "Нельзя удалить ответ завершённой попытки");
            db.update("DELETE FROM task_solutions WHERE id=?", id);
            recalculate(attemptId);
            return;
        }
        db.update("DELETE FROM " + table(type) + " WHERE id=?", id);
    }

    // ---------- Пять бизнес-операций ----------

    // 1. Опубликовать черновик, если есть задания и хватает доступных баллов.
    public Map<String, Object> publish(long examId) {
        Map<String, Object> exam = one("exams", examId);
        check(exam.get("status").equals("DRAFT"), "Экзамен уже опубликован или в архиве");
        int max = count("SELECT COALESCE(SUM(max_score), 0) FROM exam_tasks WHERE exam_id=?", examId);
        check(max > 0, "У экзамена нет заданий");
        check(((Number) exam.get("passingScore")).intValue() <= max, "Слишком большой проходной балл");
        db.update("UPDATE exams SET status='PUBLISHED' WHERE id=?", examId);
        return one("exams", examId);
    }

    // 2. Начать попытку только для опубликованного экзамена.
    public Map<String, Object> startAttempt(long examId, long userId) {
        one("users", userId);
        Map<String, Object> exam = one("exams", examId);
        check(exam.get("status").equals("PUBLISHED"), "Экзамен не опубликован");
        check(count("SELECT COUNT(*) FROM exam_attempts WHERE exam_id=? AND user_id=?", examId, userId) == 0,
                "Пользователь уже начинал этот экзамен");
        return insert("exam-attempts", Map.of("examId", examId, "userId", userId));
    }

    // 3. Добавить ответ и одновременно пересчитать баллы попытки (2 таблицы).
    public Map<String, Object> answer(long attemptId, long taskId, String answer) {
        Map<String, Object> attempt = one("exam-attempts", attemptId);
        Map<String, Object> task = one("exam-tasks", taskId);
        check(attempt.get("status").equals("IN_PROGRESS"), "Попытка уже завершена");
        check(attempt.get("examId").equals(task.get("examId")), "Задание из другого экзамена");
        check(count("SELECT COUNT(*) FROM task_solutions WHERE attempt_id=? AND task_id=?", attemptId, taskId) == 0,
                "На это задание уже ответили");
        int points = score(task, answer);
        Map<String, Object> result = insert("task-solutions", Map.of(
                "attemptId", attemptId, "taskId", taskId, "answer", answer, "points", points));
        recalculate(attemptId);
        return result;
    }

    // 4. Завершить попытку и увеличить счётчик завершённых экзаменов пользователя (2 таблицы).
    public Map<String, Object> finish(long attemptId) {
        Map<String, Object> attempt = one("exam-attempts", attemptId);
        check(attempt.get("status").equals("IN_PROGRESS"), "Попытка уже завершена");
        int tasks = count("SELECT COUNT(*) FROM exam_tasks WHERE exam_id=?", attempt.get("examId"));
        int answers = count("SELECT COUNT(*) FROM task_solutions WHERE attempt_id=?", attemptId);
        check(tasks > 0 && tasks == answers, "Сначала ответьте на все задания");
        recalculate(attemptId);
        db.update("UPDATE exam_attempts SET status='COMPLETED', finished_at=CURRENT_TIMESTAMP WHERE id=?", attemptId);
        db.update("UPDATE users SET completed_exams=completed_exams+1 WHERE id=?", attempt.get("userId"));
        return one("exam-attempts", attemptId);
    }

    // 5. Архивировать можно только экзамен без незавершённых попыток.
    public Map<String, Object> archive(long examId) {
        Map<String, Object> exam = one("exams", examId);
        check(exam.get("status").equals("PUBLISHED"), "В архив можно отправить только опубликованный экзамен");
        int active = count("SELECT COUNT(*) FROM exam_attempts WHERE exam_id=? AND status='IN_PROGRESS'", examId);
        check(active == 0, "Есть незавершённые попытки");
        db.update("UPDATE exams SET status='ARCHIVED' WHERE id=?", examId);
        return one("exams", examId);
    }

    // ---------- Небольшие вспомогательные методы ----------

    private Map<String, Object> changeAnswer(long id, String answer) {
        Map<String, Object> solution = one("task-solutions", id);
        long attemptId = ((Number) solution.get("attemptId")).longValue();
        check(one("exam-attempts", attemptId).get("status").equals("IN_PROGRESS"),
                "Нельзя менять ответ завершённой попытки");
        Map<String, Object> task = one("exam-tasks", ((Number) solution.get("taskId")).longValue());
        Map<String, Object> result = patch("task-solutions", id,
                Map.of("answer", answer, "points", score(task, answer)));
        recalculate(attemptId);
        return result;
    }

    private void recalculate(long attemptId) {
        int total = count("SELECT COALESCE(SUM(points),0) FROM task_solutions WHERE attempt_id=?", attemptId);
        db.update("UPDATE exam_attempts SET total_score=? WHERE id=?", total, attemptId);
    }

    private int score(Map<String, Object> task, String answer) {
        String correct = (String) task.get("correctAnswer");
        return correct.trim().equalsIgnoreCase(answer.trim()) ? ((Number) task.get("maxScore")).intValue() : 0;
    }

    private void draft(long examId) {
        check(one("exams", examId).get("status").equals("DRAFT"),
                "Изменять можно только черновик экзамена");
    }

    private int count(String sql, Object... args) {
        return db.queryForObject(sql, Integer.class, args);
    }

    private Map<String, Object> insert(String type, Map<String, Object> body) {
        String table = table(type);
        String columns = "";
        String marks = "";
        List<Object> values = new ArrayList<>();
        for (String key : body.keySet()) {
            if (!columns.isEmpty()) { columns += ", "; marks += ", "; }
            columns += column(key);
            marks += "?";
            values.add(body.get(key));
        }
        String sql = "INSERT INTO " + table + " (" + columns + ") VALUES (" + marks + ")";
        KeyHolder generatedId = new GeneratedKeyHolder();
        db.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, new String[]{"ID"});
            for (int i = 0; i < values.size(); i++) statement.setObject(i + 1, values.get(i));
            return statement;
        }, generatedId);
        return one(type, generatedId.getKey().longValue());
    }

    private Map<String, Object> patch(String type, long id, Map<String, Object> body) {
        String changes = "";
        List<Object> values = new ArrayList<>();
        for (String key : body.keySet()) {
            if (!changes.isEmpty()) changes += ", ";
            changes += column(key) + "=?";
            values.add(body.get(key));
        }
        values.add(id);
        db.update("UPDATE " + table(type) + " SET " + changes + " WHERE id=?", values.toArray());
        return one(type, id);
    }

    private String table(String type) {
        return switch (type) {
            case "users", "exams" -> type;
            case "exam-tasks" -> "exam_tasks";
            case "exam-attempts" -> "exam_attempts";
            case "task-solutions" -> "task_solutions";
            default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Неизвестная таблица");
        };
    }

    private String column(String name) {
        return switch (name) {
            case "passingScore" -> "passing_score";
            case "timeLimitMinutes" -> "time_limit_minutes";
            case "maxScore" -> "max_score";
            case "correctAnswer" -> "correct_answer";
            case "examId" -> "exam_id";
            case "userId" -> "user_id";
            case "taskId" -> "task_id";
            case "attemptId" -> "attempt_id";
            default -> name;
        };
    }

    private Map<String, Object> camel(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String name : row.keySet()) {
            String[] parts = name.toLowerCase(Locale.ROOT).split("_");
            String key = parts[0];
            for (int i = 1; i < parts.length; i++) {
                key += Character.toUpperCase(parts[i].charAt(0)) + parts[i].substring(1);
            }
            result.put(key, row.get(name));
        }
        return result;
    }

    private void only(Map<String, Object> body, String... allowed) {
        for (String field : body.keySet()) {
            boolean ok = false;
            for (String name : allowed) if (name.equals(field)) ok = true;
            check(ok, "Недопустимое поле: " + field);
        }
    }

    private String text(Map<String, Object> body, String name) {
        Object value = body.get(name);
        check(value instanceof String && !((String) value).isBlank(), "Нужен текст: " + name);
        return (String) value;
    }

    private long number(Map<String, Object> body, String name, long min) {
        Object value = body.get(name);
        check(value instanceof Number, "Нужно число: " + name);
        Number n = (Number) value;
        long result = n.longValue();
        check(result >= min && n.doubleValue() == result, "Некорректное число: " + name);
        return result;
    }

    private void check(boolean condition, String message) {
        if (!condition) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
