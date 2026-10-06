package com.smartrecruit.ai;

import com.smartrecruit.support.ApiTestSupport;
import com.smartrecruit.support.StubAiServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Chaîne complète dans Spring : sécurité, contrôleur, client IA, gestion d'erreurs (service IA simulé). */
class AiAdminApiTest extends ApiTestSupport {

    private static final StubAiServer AI = StubAiServer.start();
    private static final String STATUS_PATH = "/api/admin/ai/status";

    @DynamicPropertySource
    static void aiServiceUrl(DynamicPropertyRegistry registry) {
        registry.add("app.ai-service.url", AI::url);
    }

    @AfterAll
    static void stopAi() {
        AI.close();
    }

    @BeforeEach
    void resetAi() {
        AI.reset();
    }

    @Test
    void adminSeesAiStatusThroughSpringBoot() throws Exception {
        AI.respond(200, """
                {"status":"UP","service":"smartrecruit-ai","version":"0.1.0",
                 "modules":{"cv_parser":false,"matching":false,"chatbot":false,"ml":false}}
                """);

        getAs(STATUS_PATH, adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("smartrecruit-ai"))
                .andExpect(jsonPath("$.version").value("0.1.0"))
                .andExpect(jsonPath("$.modules.cv_parser").value(false))
                .andExpect(jsonPath("$.responseTimeMs").value(greaterThanOrEqualTo(0)));

        assertThat(AI.lastPath()).isEqualTo("/api/status");
        assertThat(AI.lastHeader(AiServiceClient.API_KEY_HEADER)).isEqualTo("test-only-internal-key-0123456789");
    }

    @Test
    void onlyAdminCanCheckTheAiService() throws Exception {
        getAs(STATUS_PATH, null).andExpect(status().isUnauthorized());
        getAs(STATUS_PATH, registerCandidate("cand-ai@test.com")).andExpect(status().isForbidden());
        getAs(STATUS_PATH, registerRecruiter("rec-ai@test.com")).andExpect(status().isForbidden());

        assertThat(AI.requestCount()).isZero();
    }

    @Test
    void aiServerErrorGivesClear503() throws Exception {
        AI.respond(500, "{\"message\":\"Traceback (most recent call last)\"}");

        getAs(STATUS_PATH, adminToken())
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.error").value("Service Unavailable"))
                .andExpect(jsonPath("$.message").value(AiServiceUnavailableException.UNAVAILABLE_MESSAGE))
                .andExpect(jsonPath("$.path").value(STATUS_PATH));
    }

    @Test
    void refusedInternalKeyGives503() throws Exception {
        AI.respond(401, "{\"message\":\"Clé d'API interne manquante ou invalide\"}");

        getAs(STATUS_PATH, adminToken())
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(AiServiceUnavailableException.UNAVAILABLE_MESSAGE));
    }

    @Test
    void invalidAiResponseIsNotShown() throws Exception {
        AI.respond(200, "{\"status\":\"UP\",\"service\":\"<script>\",\"version\":\"\"}");

        getAs(STATUS_PATH, adminToken())
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.service").doesNotExist());
    }

    @Test
    void slowAiServiceGives503Timeout() throws Exception {
        AI.respond(200, "{}");
        AI.delay(3_000);

        getAs(STATUS_PATH, adminToken())
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(AiServiceUnavailableException.TIMEOUT_MESSAGE));
    }
}
