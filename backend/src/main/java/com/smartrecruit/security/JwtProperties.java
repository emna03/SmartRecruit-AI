package com.smartrecruit.security;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Paramètres JWT (app.jwt.*), vérifiés au démarrage. */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        @NotBlank(message = "JWT_SECRET doit être défini")
        @Size(min = 32, message = "JWT_SECRET doit contenir au moins 32 caractères")
        String secret,

        @Min(60_000)
        long expirationMs) {
}
