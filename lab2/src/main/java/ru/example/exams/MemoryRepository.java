package ru.example.exams;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Repository
public class MemoryRepository {
    private final Map<String, List<Map<String, Object>>> data = new HashMap<>();
    private long nextId = 1;

    public MemoryRepository() {
        for (String name : List.of("exams", "exam-tasks", "exam-attempts", "task-solutions", "users")) {
            data.put(name, new ArrayList<>());
        }
    }

    public List<Map<String, Object>> all(String type) {
        if (!data.containsKey(type)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return new ArrayList<>(data.get(type));
    }

    public Map<String, Object> get(String type, long id) {
        for (var item : all(type)) {
            if (((Number) item.get("id")).longValue() == id) return item;
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    public void add(String type, Map<String, Object> item) {
        all(type); // Проверяем название ресурса.
        item.put("id", nextId++);
        data.get(type).add(item);
    }

    public void delete(String type, long id) {
        data.get(type).remove(get(type, id));
    }
}
