package com.example.test.dto;

import com.example.test.model.Role;

import java.time.Instant;

public record TokenResponse(String accessToken, String tokenType, long expiresIn, Instant expiresAt, Role role) {

    public static TokenResponse bearer(String token, Instant expiresAt, Role role) {
        long seconds = Math.max(0, expiresAt.getEpochSecond() - Instant.now().getEpochSecond());
        return new TokenResponse(token, "Bearer", seconds, expiresAt, role);
    }
}
