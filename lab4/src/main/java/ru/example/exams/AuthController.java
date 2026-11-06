package ru.example.exams;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
public class AuthController {
    private final JdbcTemplate db;
    private final PasswordEncoder encoder;

    AuthController(JdbcTemplate db, PasswordEncoder encoder) {
        this.db = db;
        this.encoder = encoder;
    }

    record Registration(String username, String password) {}

    @GetMapping("/csrf")
    CsrfToken csrf(CsrfToken token) { return token; }

    @PostMapping("/register")
    ResponseEntity<String> register(@RequestBody Registration r) {
        if (r.username() == null || !r.username().matches("[A-Za-z0-9_]{3,30}")
                || r.password() == null || !r.password().matches("^(?=.*[A-Za-z])(?=.*[0-9]).{8,72}$"))
            return ResponseEntity.badRequest().body("Логин: 3-30 символов; пароль: от 8, буквы и цифры");
        try {
            db.update("INSERT INTO users(name, username, password, role, enabled) VALUES (?, ?, ?, 'USER', TRUE)",
                    r.username(), r.username(), encoder.encode(r.password()));
            return ResponseEntity.ok("Пользователь зарегистрирован");
        } catch (DuplicateKeyException e) {
            return ResponseEntity.badRequest().body("Логин занят");
        }
    }
}
