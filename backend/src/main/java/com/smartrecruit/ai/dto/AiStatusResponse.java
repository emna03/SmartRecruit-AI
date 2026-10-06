package com.smartrecruit.ai.dto;

import java.util.Map;

/** État du service IA vu par Spring Boot (réservé à l'administrateur). */
public record AiStatusResponse(
        String status,
        String service,
        String version,
        Map<String, Boolean> modules,
        long responseTimeMs) {

    public static AiStatusResponse of(AiServiceStatus status, long responseTimeMs) {
        return new AiStatusResponse(status.status(), status.service(), status.version(), status.modules(),
                responseTimeMs);
    }
}
