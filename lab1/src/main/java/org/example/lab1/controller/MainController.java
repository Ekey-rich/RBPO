package org.example.lab1.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MainController {

    // Эндпоинт 1: возвращает текст
    @GetMapping("/hello")
    public String hello() {
        return "Hello from Spring Boot!";
    }

    // Эндпоинт 2: возвращает число (сумму двух параметров)
    @GetMapping("/sum")
    public int sum(@RequestParam int a, @RequestParam int b) {
        return a + b;
    }
}
