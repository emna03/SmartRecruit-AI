package com.smartrecruit.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Map;

/** Réponse de GET /api/status du service IA, validée avant usage. */
public record AiServiceStatus(
        @NotNull @Pattern(regexp = "UP") String status,
        @NotBlank @Size(max = 100) String service,
        @NotBlank @Size(max = 50) String version,
        @NotNull Map<@NotBlank String, @NotNull Boolean> modules) {
}
