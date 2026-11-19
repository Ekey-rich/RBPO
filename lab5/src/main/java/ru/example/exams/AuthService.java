package ru.example.exams;

import com.auth0.jwt.exceptions.JWTVerificationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
@Transactional
public class AuthService {
    private final JdbcTemplate db;
    private final PasswordEncoder encoder;
    private final UserDetailsService users;
    private final JwtService jwt;
    private final SessionRepository sessions;

    AuthService(JdbcTemplate db, PasswordEncoder encoder, UserDetailsService users,
                JwtService jwt, SessionRepository sessions) {
        this.db = db;
        this.encoder = encoder;
        this.users = users;
        this.jwt = jwt;
        this.sessions = sessions;
    }

    public record TokenPair(String accessToken, String refreshToken) {}

    public TokenPair register(String username, String password, String deviceId) {
        if (username == null || !username.matches("[A-Za-z0-9_]{3,30}")
                || password == null || !password.matches("^(?=.*[A-Za-z])(?=.*[0-9]).{8,72}$")
                || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Проверьте логин и пароль");
        checkDevice(deviceId);
        try {
            db.update("INSERT INTO users(name, username, password, role, enabled) VALUES (?, ?, ?, 'USER', TRUE)",
                    username, username, encoder.encode(password));
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Логин занят");
        }
        return issue(username, deviceId);
    }

    public TokenPair login(String username, String password, String deviceId) {
        if (username == null || username.isBlank() || password == null || password.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите логин и пароль");
        checkDevice(deviceId);
        if (!encoder.matches(password, activeUser(username).getPassword())) throw unauthorized();
        return issue(username, deviceId);
    }

    // Отказ повторному refresh должен сохранить REVOKED, несмотря на ответ 401.
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public TokenPair refresh(String token) {
        if (token == null || token.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите refreshToken");
        String username;
        try {
            username = jwt.checkRefresh(token).getSubject();
        } catch (JWTVerificationException | IllegalArgumentException e) {
            throw unauthorized();
        }
        UserSession old = sessions.findForUpdate(token).orElseThrow(AuthService::unauthorized);
        if (old.status() != SessionStatus.ACTIVE) {
            sessions.setStatus(old.id(), SessionStatus.REVOKED);
            throw unauthorized();
        }
        if (!old.username().equals(username)) throw unauthorized();
        activeUser(username);
        sessions.setStatus(old.id(), SessionStatus.USED);
        return issue(username, old.deviceId());
    }

    private TokenPair issue(String username, String deviceId) {
        String access = jwt.createAccess(username);
        String refresh = jwt.createRefresh(username);
        sessions.save(new UserSession(UUID.randomUUID(), username, deviceId, access, refresh,
                jwt.checkAccess(access).getExpiresAtAsInstant(), jwt.checkRefresh(refresh).getExpiresAtAsInstant(),
                SessionStatus.ACTIVE));
        return new TokenPair(access, refresh);
    }

    private UserDetails activeUser(String username) {
        try {
            UserDetails user = users.loadUserByUsername(username);
            if (!user.isEnabled()) throw unauthorized();
            return user;
        } catch (UsernameNotFoundException e) {
            throw unauthorized();
        }
    }

    private void checkDevice(String deviceId) {
        if (deviceId != null && deviceId.length() > 255)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Слишком длинный deviceId");
    }

    private static ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Неверные данные входа или refresh");
    }
}
