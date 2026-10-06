package com.smartrecruit.ai.controller;

import com.smartrecruit.ai.AiServiceClient;
import com.smartrecruit.ai.dto.AiServiceStatus;
import com.smartrecruit.ai.dto.AiStatusResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/** Vérifie la liaison Spring Boot -> FastAPI (clé interne comprise) ; 503 si le service IA ne répond pas. */
@RestController
@RequestMapping("/api/admin/ai")
@RequiredArgsConstructor
public class AiAdminController {

    private final AiServiceClient aiServiceClient;

    @GetMapping("/status")
    public AiStatusResponse status() {
        long start = System.nanoTime();
        AiServiceStatus status = aiServiceClient.get("/api/status", AiServiceStatus.class);
        return AiStatusResponse.of(status, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
    }
}
