package ru.example.exams;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class ExamController {
    private final ExamService service;

    public ExamController(ExamService service) {
        this.service = service;
    }

    // CRUD для всех пяти таблиц.
    @GetMapping("/{type}")
    public Object all(@PathVariable String type) {
        return service.all(type);
    }

    @GetMapping("/{type}/{id}")
    public Object one(@PathVariable String type, @PathVariable long id) {
        return service.one(type, id);
    }

    @PostMapping("/{type}")
    @ResponseStatus(HttpStatus.CREATED)
    public Object create(@PathVariable String type, @RequestBody Map<String, Object> body) {
        return service.create(type, body);
    }

    @PatchMapping("/{type}/{id}")
    public Object change(@PathVariable String type, @PathVariable long id,
                         @RequestBody Map<String, Object> body) {
        return service.change(type, id, body);
    }

    @DeleteMapping("/{type}/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String type, @PathVariable long id) {
        service.delete(type, id);
    }

    // Пять бизнес-операций.
    @PostMapping("/operations/publish/{examId}")
    public Object publish(@PathVariable long examId) {
        return service.publish(examId);
    }

    @PostMapping("/operations/start-attempt")
    public Object start(@RequestBody Map<String, Object> body) {
        return service.create("exam-attempts", body);
    }

    @PostMapping("/operations/answer")
    public Object answer(@RequestBody Map<String, Object> body) {
        return service.create("task-solutions", body);
    }

    @PostMapping("/operations/finish/{attemptId}")
    public Object finish(@PathVariable long attemptId) {
        return service.finish(attemptId);
    }

    @PostMapping("/operations/archive/{examId}")
    public Object archive(@PathVariable long examId) {
        return service.archive(examId);
    }

    // Ошибки ограничений базы данных отдаются как HTTP 409, а не 500.
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> databaseError() {
        return Map.of("error", "Операция нарушает ограничение базы данных");
    }
}
