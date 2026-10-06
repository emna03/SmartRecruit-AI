package com.smartrecruit.ai;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Accès au service IA FastAPI (app.ai-service.*), vérifié au démarrage.
 * Clé vide : l'application démarre, mais toute fonctionnalité IA répond 503.
 */
@Validated
@ConfigurationProperties(prefix = "app.ai-service")
public record AiServiceProperties(
        @NotBlank(message = "AI_SERVICE_URL doit être défini")
        @Pattern(regexp = "^https?://\\S+$", message = "AI_SERVICE_URL doit être une URL http(s)")
        String url,

        @Pattern(regexp = "^$|^(?!change_me)\\S{16,}$",
                message = "AI_INTERNAL_API_KEY doit contenir au moins 16 caractères et ne pas être la valeur d'exemple")
        String apiKey,

        @Min(value = 1, message = "AI_TIMEOUT_SECONDS doit être compris entre 1 et 120")
        @Max(value = 120, message = "AI_TIMEOUT_SECONDS doit être compris entre 1 et 120")
        int timeoutSeconds) {

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
