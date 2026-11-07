package com.example.test.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "wallet.security")
public record WalletSecurityProperties(@Valid @NotNull Jwt jwt, @Valid @NotNull Admin admin, @Valid @NotNull Pin pin) {

    public record Jwt(
            @NotBlank @Size(min = 32, message = "must be at least 32 characters for HS256") String secret,
            @NotBlank String issuer,
            @NotNull Duration ttl) {
    }

    /** Transaction PIN lockout policy: {@code maxAttempts} wrong PINs in a row lock it for {@code lockDuration}. */
    public record Pin(@Min(1) int maxAttempts, @NotNull Duration lockDuration) {
    }

    /** Optional bootstrap admin; skipped when email is blank. */
    public record Admin(String email, String password) {
    }
}
