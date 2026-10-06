package com.smartrecruit.ai;

import com.smartrecruit.ai.dto.AiServiceStatus;
import com.smartrecruit.config.AiServiceConfig;
import com.smartrecruit.support.StubAiServer;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Client Spring Boot -> FastAPI face à un vrai serveur HTTP local simulant le service IA. */
class AiServiceClientTest {

    private static final String KEY = "test-only-internal-key-0123456789";
    private static final String STATUS_JSON = """
            {"status":"UP","service":"smartrecruit-ai","version":"0.1.0",
             "modules":{"cv_parser":false,"matching":false,"chatbot":false,"ml":false}}
            """;

    private static ValidatorFactory validatorFactory;
    private StubAiServer server;

    @BeforeAll
    static void createValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @BeforeEach
    void startServer() {
        server = StubAiServer.start();
    }

    @AfterEach
    void stopServer() {
        server.close();
    }

    private AiServiceClient client(String url, String key, int timeoutSeconds) {
        AiServiceProperties properties = new AiServiceProperties(url, key, timeoutSeconds);
        Validator validator = validatorFactory.getValidator();
        return new AiServiceClient(AiServiceConfig.buildRestClient(RestClient.builder(), properties), properties,
                validator);
    }

    private AiServiceClient client() {
        return client(server.url(), KEY, 2);
    }

    @Test
    void sendsInternalKeyAndReturnsValidatedResponse() {
        server.respond(200, STATUS_JSON);

        AiServiceStatus status = client().get("/api/status", AiServiceStatus.class);

        assertThat(status.status()).isEqualTo("UP");
        assertThat(status.service()).isEqualTo("smartrecruit-ai");
        assertThat(status.modules()).containsEntry("cv_parser", false).hasSize(4);
        assertThat(server.lastMethod()).isEqualTo("GET");
        assertThat(server.lastPath()).isEqualTo("/api/status");
        assertThat(server.lastHeader(AiServiceClient.API_KEY_HEADER)).isEqualTo(KEY);
        assertThat(server.lastHeader(AiServiceClient.REQUEST_ID_HEADER)).matches("[0-9a-f]{32}");
    }

    @Test
    void postSendsJsonBody() {
        server.respond(200, STATUS_JSON);

        client().post("/api/echo", Map.of("skill", "Java"), AiServiceStatus.class);

        assertThat(server.lastMethod()).isEqualTo("POST");
        assertThat(server.lastHeader("Content-Type")).startsWith("application/json");
        assertThat(server.lastBody()).isEqualTo("{\"skill\":\"Java\"}");
        assertThat(server.lastHeader(AiServiceClient.API_KEY_HEADER)).isEqualTo(KEY);
    }

    @Test
    void refusedKeyBecomesServiceUnavailable() {
        server.respond(401, """
                {"status":401,"error":"Unauthorized","message":"Clé d'API interne manquante ou invalide"}
                """);

        assertUnavailable(AiServiceUnavailableException.Reason.AUTH_REJECTED);
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 502, 503})
    void serverErrorBecomesServiceUnavailable(int status) {
        server.respond(status, "{\"message\":\"Traceback interne\"}");

        assertUnavailable(AiServiceUnavailableException.Reason.UPSTREAM_ERROR);
    }

    @Test
    void slowServiceTimesOut() {
        server.respond(200, STATUS_JSON);
        server.delay(3_000);
        AiServiceClient client = client(server.url(), KEY, 1);

        long start = System.nanoTime();
        assertThatThrownBy(() -> client.get("/api/status", AiServiceStatus.class))
                .isInstanceOfSatisfying(AiServiceUnavailableException.class, ex -> {
                    assertThat(ex.getReason()).isEqualTo(AiServiceUnavailableException.Reason.TIMEOUT);
                    assertThat(ex.getMessage()).isEqualTo(AiServiceUnavailableException.TIMEOUT_MESSAGE);
                });
        assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(2_500);
    }

    @Test
    void stoppedServiceIsUnreachable() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        AiServiceClient client = client("http://127.0.0.1:" + closedPort, KEY, 2);

        assertThatThrownBy(() -> client.get("/api/status", AiServiceStatus.class))
                .isInstanceOfSatisfying(AiServiceUnavailableException.class, ex ->
                        assertThat(ex.getReason()).isEqualTo(AiServiceUnavailableException.Reason.UNREACHABLE));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"status\":\"DOWN\",\"service\":\"smartrecruit-ai\",\"version\":\"0.1.0\",\"modules\":{}}",
            "{\"status\":\"UP\",\"service\":\"smartrecruit-ai\",\"modules\":{}}",
            "{\"status\":\"UP\",\"service\":\"\",\"version\":\"0.1.0\",\"modules\":{}}",
            "{\"status\":\"UP\",\"service\":\"smartrecruit-ai\",\"version\":\"0.1.0\"}",
            "{\"status\":\"UP\",\"service\":\"smartrecruit-ai\",\"version\":\"0.1.0\",\"modules\":{\"ml\":null}}",
            "<html>not json</html>",
            "[]",
            ""
    })
    void invalidResponseIsRejected(String body) {
        server.respond(200, body);

        assertUnavailable(AiServiceUnavailableException.Reason.INVALID_RESPONSE);
    }

    @Test
    void rejectedDataKeepsTheServiceMessage() {
        server.respond(422, """
                {"status":422,"error":"Unprocessable Content","message":"Le fichier PDF est illisible"}
                """);

        assertThatThrownBy(() -> client().get("/api/status", AiServiceStatus.class))
                .isInstanceOfSatisfying(AiServiceRequestException.class, ex -> {
                    assertThat(ex.getUpstreamStatus()).isEqualTo(422);
                    assertThat(ex.getMessage()).isEqualTo("Le fichier PDF est illisible");
                });
    }

    @Test
    void rejectedDataWithoutReadableMessageUsesDefault() {
        server.respond(400, "pas du json");

        assertThatThrownBy(() -> client().get("/api/status", AiServiceStatus.class))
                .isInstanceOf(AiServiceRequestException.class)
                .hasMessage(AiServiceClient.DEFAULT_REJECTION_MESSAGE);
    }

    @Test
    void overlongServiceMessageIsTruncated() {
        server.respond(400, "{\"message\":\"" + "x".repeat(1_000) + "\"}");

        assertThatThrownBy(() -> client().get("/api/status", AiServiceStatus.class))
                .isInstanceOf(AiServiceRequestException.class)
                .satisfies(ex -> assertThat(ex.getMessage()).hasSize(300));
    }

    @Test
    void missingKeyFailsWithoutCallingTheService() {
        AiServiceClient client = client(server.url(), "", 2);

        assertThatThrownBy(() -> client.get("/api/status", AiServiceStatus.class))
                .isInstanceOfSatisfying(AiServiceUnavailableException.class, ex ->
                        assertThat(ex.getReason()).isEqualTo(AiServiceUnavailableException.Reason.NOT_CONFIGURED));
        assertThat(server.requestCount()).isZero();
    }

    private void assertUnavailable(AiServiceUnavailableException.Reason reason) {
        assertThatThrownBy(() -> client().get("/api/status", AiServiceStatus.class))
                .isInstanceOfSatisfying(AiServiceUnavailableException.class, ex -> {
                    assertThat(ex.getReason()).isEqualTo(reason);
                    assertThat(ex.getMessage()).isEqualTo(AiServiceUnavailableException.UNAVAILABLE_MESSAGE);
                });
    }
}
