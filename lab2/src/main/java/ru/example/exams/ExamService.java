package ru.example.exams;

import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;

@Service
public class ExamService {
    private final MemoryRepository repo;

    public ExamService(MemoryRepository repo) {
        this.repo = repo;
    }

    // GET: все записи.
    public List<Map<String, Object>> all(String type) {
        return repo.all(type);
    }

    // GET: одна запись по ID.
    public Map<String, Object> one(String type, long id) {return repo.get(type, id); }

    // POST: новая запись.
    public Map<String, Object> create(String type, Map<String, Object> body) {
        var item = new HashMap<>(body);
        item.remove("id");
        if (type.equals("exam-attempts")) {
            String now = Instant.now().toString();
            item.put("startedAt", now);
            item.put("finishedAt", "COMPLETED".equals(item.get("status")) ? now : null);
            item.putIfAbsent("status", "IN_PROGRESS");
            item.putIfAbsent("totalScore", 0);
        }
        repo.add(type, item);
        return item;
    }

    // PATCH: меняем только переданные поля.
    public Map<String, Object> update(String type, long id, Map<String, Object> body) {
        var item = repo.get(type, id);
        Object start = item.get("startedAt");
        Object finish = item.get("finishedAt");
        item.putAll(body);
        item.put("id", id);
        if (type.equals("exam-attempts")) {
            item.put("startedAt", start);
            item.put("finishedAt", finish);
            if ("COMPLETED".equals(item.get("status")) && finish == null) {
                item.put("finishedAt", Instant.now().toString());
            }
        }
        return item;
    }

    // DELETE: сначала удаляем зависимые записи, затем сам объект.
    public void delete(String type, long id) {
        repo.get(type, id);
        if (type.equals("exams")) {
            removeRelated("exam-tasks", "examId", id);
            removeRelated("exam-attempts", "examId", id);
        }
        if (type.equals("users")) removeRelated("exam-attempts", "userId", id);
        if (type.equals("exam-tasks")) removeRelated("task-solutions", "taskId", id);
        if (type.equals("exam-attempts")) removeRelated("task-solutions", "attemptId", id);
        repo.delete(type, id);
    }

    private void removeRelated(String type, String field, long id) {
        for (var item : repo.all(type)) {
            if (item.get(field) instanceof Number n && n.longValue() == id) {
                delete(type, ((Number) item.get("id")).longValue());
            }
        }
    }
}
