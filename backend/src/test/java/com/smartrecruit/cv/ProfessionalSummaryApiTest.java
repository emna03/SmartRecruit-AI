package com.smartrecruit.cv;

import com.smartrecruit.support.ApiTestSupport;
import com.smartrecruit.support.StubAiServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Résumé professionnel LLM : Spring récupère le CV analysé, appelle FastAPI (simulé), valide la réponse. */
class ProfessionalSummaryApiTest extends ApiTestSupport {

    private static final StubAiServer AI = StubAiServer.start();
    private static final Path UPLOADS = createUploadDir();
    private static final String CV_URL = "/api/candidate/cv";
    private static final String SUMMARY_URL = "/api/candidate/cv/professional-summary";
    private static final String SUMMARY_PATH = "/api/llm/professional-summary";
    private static final byte[] PDF = "%PDF-1.4\n% résumé IA\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);
    private static final String PARSE_RESULT = resource("/cv/parse-result.json");
    private static final String SUMMARY_RESULT = resource("/cv/professional-summary.json");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.ai-service.url", AI::url);
        registry.add("app.upload-dir", UPLOADS::toString);
    }

    @AfterAll
    static void stopAi() {
        AI.close();
    }

    @BeforeEach
    void reset() throws IOException {
        AI.reset();
        AI.respondTo("/api/cv/parse", 200, PARSE_RESULT);
        for (Path file : Files.isDirectory(UPLOADS.resolve("cvs"))
                ? Files.list(UPLOADS.resolve("cvs")).toList()
                : java.util.List.<Path>of()) {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void candidateGetsAProfessionalSummaryFromAnalyzedCv() throws Exception {
        String token = registerCandidate("summary-ok@test.com");
        uploadAnalyzed(token);
        AI.respondTo(SUMMARY_PATH, 200, SUMMARY_RESULT);

        summarize(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value(org.hamcrest.Matchers.containsString("Java")))
                .andExpect(jsonPath("$.keywords", hasSize(5)))
                .andExpect(jsonPath("$.model").value("gemini-3.5-flash-lite"))
                .andExpect(jsonPath("$.provider").value("gemini"))
                .andExpect(jsonPath("$.promptVersion").value("1.0.0"))
                .andExpect(jsonPath("$.source").value("llm"));

        assertThat(AI.lastPath()).isEqualTo(SUMMARY_PATH);
        assertThat(AI.lastHeader("X-Internal-Api-Key")).isNotBlank();
        String body = AI.lastBody();
        assertThat(body).contains("\"technologies\"");
        assertThat(body).contains("Java");
        assertThat(body).doesNotContain("Camille");
        assertThat(body).doesNotContain("email");
        assertThat(body).doesNotContain("\"text\"");
        assertThat(body).doesNotContain("userId");
    }

    @Test
    void unanalyzedCvIsRejected() throws Exception {
        String token = registerCandidate("summary-raw@test.com");
        AI.respondTo("/api/cv/parse", 503, """
                {"status":503,"error":"Service Unavailable","message":"IA indisponible"}
                """);
        mvc.perform(multipart(CV_URL).file(pdf()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        AI.reset();

        summarize(token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("analysé")));
        assertThat(AI.requestCount(SUMMARY_PATH)).isZero();
    }

    @Test
    void anonymousAndWrongRoleAreRejected() throws Exception {
        String candidate = registerCandidate("summary-sec@test.com");
        uploadAnalyzed(candidate);
        AI.respondTo(SUMMARY_PATH, 200, SUMMARY_RESULT);

        summarize(null).andExpect(status().isUnauthorized());
        summarize(registerRecruiter("summary-rec@test.com")).andExpect(status().isForbidden());
        summarize(adminToken()).andExpect(status().isForbidden());
    }

    @Test
    void otherCandidateCannotTriggerSummaryOnSomeoneElseCvImplicitly() throws Exception {
        String owner = registerCandidate("summary-owner@test.com");
        uploadAnalyzed(owner);
        AI.respondTo(SUMMARY_PATH, 200, SUMMARY_RESULT);

        String other = registerCandidate("summary-other@test.com");
        summarize(other)
                .andExpect(status().isNotFound());
        assertThat(AI.requestCount(SUMMARY_PATH)).isZero();
    }

    @Test
    void aiUnavailableIsMappedTo503() throws Exception {
        String token = registerCandidate("summary-down@test.com");
        uploadAnalyzed(token);
        AI.respondTo(SUMMARY_PATH, 503, """
                {"status":503,"error":"Service Unavailable","message":"Le fournisseur LLM est indisponible."}
                """);

        summarize(token)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("indisponible")));
    }

    @Test
    void invalidAiResponseIsRejected() throws Exception {
        String token = registerCandidate("summary-bad@test.com");
        uploadAnalyzed(token);
        AI.respondTo(SUMMARY_PATH, 200, """
                {"summary":"trop court","keywords":[],"model":"x","provider":"gemini","promptVersion":"1","source":"llm"}
                """);

        summarize(token).andExpect(status().isServiceUnavailable());
    }

    private void uploadAnalyzed(String token) throws Exception {
        AI.respondTo("/api/cv/parse", 200, PARSE_RESULT);
        mvc.perform(multipart(CV_URL).file(pdf()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parsingStatus").value("TERMINE"));
    }

    private ResultActions summarize(String token) throws Exception {
        var request = post(SUMMARY_URL);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mvc.perform(request);
    }

    private static MockMultipartFile pdf() {
        return new MockMultipartFile("file", "cv.pdf", "application/pdf", PDF);
    }

    private static Path createUploadDir() {
        try {
            Path dir = Files.createTempDirectory("smartrecruit-summary-test");
            dir.toFile().deleteOnExit();
            return dir;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String resource(String path) {
        try (InputStream in = ProfessionalSummaryApiTest.class.getResourceAsStream(path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
