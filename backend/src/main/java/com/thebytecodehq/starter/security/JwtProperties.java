package com.thebytecodehq.starter.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Validated at startup, so a missing or weak signing secret stops the application instead of weakening it. */
@Validated
@ConfigurationProperties("app.jwt")
public record JwtProperties(
        @NotBlank @Size(min = 32, message = "app.jwt.secret must be at least 32 characters (set APP_JWT_SECRET)")
        String secret,
        @NotBlank String issuer,
        @NotNull Duration ttl) {
}
