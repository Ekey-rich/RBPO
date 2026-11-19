package ru.example.exams;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

@Repository
public class SessionRepository {
    private final JdbcTemplate db;

    SessionRepository(JdbcTemplate db) { this.db = db; }

    public Optional<UserSession> findForUpdate(String refreshToken) {
        return db.query("SELECT * FROM user_sessions WHERE refresh_token=? FOR UPDATE", (rs, n) ->
                new UserSession(rs.getObject("id", UUID.class), rs.getString("username"),
                        rs.getString("device_id"), rs.getString("access_token"), rs.getString("refresh_token"),
                        rs.getTimestamp("access_token_expiry").toInstant(),
                        rs.getTimestamp("refresh_token_expiry").toInstant(),
                        SessionStatus.valueOf(rs.getString("status"))), refreshToken).stream().findFirst();
    }

    public void save(UserSession s) {
        db.update("""
                INSERT INTO user_sessions(id, username, device_id, access_token, refresh_token,
                                          access_token_expiry, refresh_token_expiry, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, s.id(), s.username(), s.deviceId(), s.accessToken(), s.refreshToken(),
                Timestamp.from(s.accessTokenExpiry()), Timestamp.from(s.refreshTokenExpiry()), s.status().name());
    }

    public void setStatus(UUID id, SessionStatus status) {
        db.update("UPDATE user_sessions SET status=? WHERE id=?", status.name(), id);
    }
}
