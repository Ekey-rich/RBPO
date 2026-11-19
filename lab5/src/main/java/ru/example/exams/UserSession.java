package ru.example.exams;

import java.time.Instant;
import java.util.UUID;

public record UserSession(UUID id, String username, String deviceId,
                          String accessToken, String refreshToken,
                          Instant accessTokenExpiry, Instant refreshTokenExpiry,
                          SessionStatus status) {}
