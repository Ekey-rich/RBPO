package ru.example.exams;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService service;

    AuthController(AuthService service) { this.service = service; }

    record Credentials(String username, String password, String deviceId) {}
    record Refresh(String refreshToken) {}

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    AuthService.TokenPair register(@RequestBody Credentials r) {
        return service.register(r.username(), r.password(), r.deviceId());
    }

    @PostMapping("/login")
    AuthService.TokenPair login(@RequestBody Credentials r) {
        return service.login(r.username(), r.password(), r.deviceId());
    }

    @PostMapping("/refresh")
    AuthService.TokenPair refresh(@RequestBody Refresh r) {
        return service.refresh(r.refreshToken());
    }

    @ExceptionHandler(DataAccessException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    Map<String, String> databaseError() {
        return Map.of("error", "Не удалось сохранить данные");
    }
}
