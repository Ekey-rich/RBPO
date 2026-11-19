package ru.example.exams;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class JwtService {
    private final Algorithm algorithm;
    private final long accessSeconds;
    private final long refreshSeconds;

    JwtService(@Value("${jwt.secret}") String secret,
               @Value("${jwt.access-seconds:900}") long accessSeconds,
               @Value("${jwt.refresh-seconds:604800}") long refreshSeconds) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32 || accessSeconds <= 0 || refreshSeconds <= 0)
            throw new IllegalArgumentException("JWT: секрет от 32 байт, время жизни больше нуля");
        this.algorithm = Algorithm.HMAC256(secret);
        this.accessSeconds = accessSeconds;
        this.refreshSeconds = refreshSeconds;
    }

    public String createAccess(String username) { return create(username, "access", accessSeconds); }
    public String createRefresh(String username) { return create(username, "refresh", refreshSeconds); }
    public DecodedJWT checkAccess(String token) { return check(token, "access"); }
    public DecodedJWT checkRefresh(String token) { return check(token, "refresh"); }

    private String create(String username, String type, long seconds) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        return JWT.create().withSubject(username).withIssuedAt(now).withExpiresAt(now.plusSeconds(seconds))
                .withJWTId(UUID.randomUUID().toString()).withClaim("type", type).sign(algorithm);
    }

    private DecodedJWT check(String token, String type) {
        DecodedJWT jwt = JWT.require(algorithm).withClaim("type", type)
                .withClaimPresence("sub").withClaimPresence("iat")
                .withClaimPresence("exp").withClaimPresence("jti").build().verify(token);
        if (jwt.getSubject() == null || jwt.getSubject().isBlank() || jwt.getId() == null || jwt.getId().isBlank()
                || jwt.getIssuedAtAsInstant() == null || jwt.getExpiresAtAsInstant() == null
                || !jwt.getExpiresAtAsInstant().isAfter(jwt.getIssuedAtAsInstant()))
            throw new JWTVerificationException("Отсутствуют обязательные данные JWT");
        return jwt;
    }
}
