package com.smartrecruit.turnover;

import com.smartrecruit.support.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Service IA arrêté (port fermé) : la prédiction répond 503 avec un message clair. */
class TurnoverAiUnreachableTest extends ApiTestSupport {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.ai-service.url", () -> "http://127.0.0.1:" + closedPort());
    }

    @Test
    void stoppedAiServiceGivesA503() throws Exception {
        String bob = registerRecruiter("bob@test.com");

        postJson("/api/hr/turnover/predictions", bob, resource("/turnover/low-risk-request.json"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Le service IA est momentanément indisponible. Réessayez plus tard."));
    }

    private static int closedPort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String resource(String path) {
        try (InputStream in = TurnoverAiUnreachableTest.class.getResourceAsStream(path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
