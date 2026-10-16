package ru.example.exams;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final ExamService service;

    public ApiController(ExamService service) {
        this.service = service;
    }

    @GetMapping("/{type}")
    public List<Map<String, Object>> all(@PathVariable String type) {
        return service.all(type);
    }

    @GetMapping("/{type}/{id}")
    public Map<String, Object> one(@PathVariable String type, @PathVariable Long id) {
        return service.one(type, id);
    }

    @PostMapping("/{type}")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@PathVariable String type, @RequestBody Map<String, Object> body) {
        return service.create(type, body);
    }

    @PatchMapping("/{type}/{id}")
    public Map<String, Object> update(@PathVariable String type, @PathVariable Long id,
                                      @RequestBody Map<String, Object> body) {
        return service.update(type, id, body);
    }

    @DeleteMapping("/{type}/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String type, @PathVariable Long id) {
        service.delete(type, id);
    }
}
