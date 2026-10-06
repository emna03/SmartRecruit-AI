package com.smartrecruit.cv;

import com.smartrecruit.ai.AiServiceClient;
import com.smartrecruit.candidate.entity.CandidateProfile;
import com.smartrecruit.candidate.repository.CandidateProfileRepository;
import com.smartrecruit.cv.entity.Cv;
import com.smartrecruit.cv.entity.CvParsingStatus;
import com.smartrecruit.skill.entity.Skill;
import com.smartrecruit.skill.repository.SkillRepository;
import com.smartrecruit.support.ApiTestSupport;
import com.smartrecruit.support.StubAiServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Parcours CV dans Spring : sécurité, validation du fichier, appel au CV Parser (simulé), MySQL (H2), disque. */
class CandidateCvApiTest extends ApiTestSupport {

    private static final StubAiServer AI = StubAiServer.start();
    private static final Path UPLOADS = createUploadDir();
    private static final String CV_URL = "/api/candidate/cv";
    private static final byte[] PDF = "%PDF-1.4\n% CV fictif de test\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);
    private static final String PARSE_RESULT = resource("/cv/parse-result.json");
    private static final String UNAVAILABLE = "Le service IA est momentanément indisponible. Réessayez plus tard.";
    private static final String TIMEOUT = "Le service IA n'a pas répondu à temps. Réessayez dans quelques instants.";

    @Autowired
    private CandidateProfileRepository profileRepository;

    @Autowired
    private SkillRepository skillRepository;

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
        AI.respond(200, PARSE_RESULT);
        for (Path file : storedFiles()) {
            Files.delete(file);
        }
    }

    @Test
    void candidateUploadsPdfAndGetsStructuredAnalysis() throws Exception {
        String token = registerCandidate("cv-upload@test.com");

        upload(token, new MockMultipartFile("file", "Mon CV.pdf", "application/pdf", PDF))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalFileName").value("Mon CV.pdf"))
                .andExpect(jsonPath("$.fileSize").value(PDF.length))
                .andExpect(jsonPath("$.parsingStatus").value("TERMINE"))
                .andExpect(jsonPath("$.parsingError").doesNotExist())
                .andExpect(jsonPath("$.analysis.technologies[*].name")
                        .value(containsInAnyOrder("Java", "Spring Boot", "Angular", "MySQL")))
                .andExpect(jsonPath("$.analysis.educations[0].level").value("BAC_PLUS_5"))
                .andExpect(jsonPath("$.analysis.experiences[0].jobTitle").value("Développeuse Full Stack"))
                .andExpect(jsonPath("$.analysis.languages[1].cefr").value("B2"))
                .andExpect(jsonPath("$.analysis.text").doesNotExist());

        assertThat(AI.lastMethod()).isEqualTo("POST");
        assertThat(AI.lastPath()).isEqualTo("/api/cv/parse");
        assertThat(AI.lastHeader("Content-Type")).isEqualTo("application/pdf");
        assertThat(AI.lastHeader(AiServiceClient.API_KEY_HEADER)).isEqualTo("test-only-internal-key-0123456789");
        assertThat(AI.lastBodyBytes()).isEqualTo(PDF);

        Cv cv = cvOf("cv-upload@test.com");
        assertThat(cv.getParsingStatus()).isEqualTo(CvParsingStatus.TERMINE);
        assertThat(cv.getParsedAt()).isNotNull();
        assertThat(cv.getContentType()).isEqualTo("application/pdf");
        assertThat(cv.getStoredFileName()).matches("[0-9a-f]{32}\\.pdf");
        assertThat(cv.getExtractedText()).startsWith("Camille Exemple\n[email] | [téléphone]");
        assertThat(cv.getExtractedData()).contains("\"technologies\"").doesNotContain("\"text\"");
        assertThat(cv.getExtractedSkills()).extracting(Skill::getName)
                .containsExactlyInAnyOrder("Java", "Spring Boot", "Angular", "MySQL", "API REST", "Scrum");
        assertThat(skillRepository.findByNameIgnoreCase("Spring Boot")).get()
                .extracting(Skill::getCategory).isEqualTo("Framework back-end");
        assertThat(Files.readAllBytes(UPLOADS.resolve("cvs").resolve(cv.getStoredFileName()))).isEqualTo(PDF);

        getAs(CV_URL, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(cv.getId()))
                .andExpect(jsonPath("$.analysis.skills", hasSize(2)));
    }

    @Test
    void browserFileNameAndMimeTypeAreNotTrusted() throws Exception {
        String token = registerCandidate("cv-mime@test.com");

        upload(token, new MockMultipartFile("file", "../../etc/cv<script>.txt", "text/plain", PDF))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalFileName").value("cv_script_.txt.pdf"));
        assertThat(cvOf("cv-mime@test.com").getContentType()).isEqualTo("application/pdf");

        AI.reset();
        upload(token, new MockMultipartFile("file", "cv.pdf", "application/pdf", "MZ\u0090 exécutable".getBytes()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Le fichier n'est pas un PDF valide. Seuls les CV au format PDF sont acceptés."));
        assertThat(AI.requestCount()).isZero();
    }

    @Test
    void emptyMissingAndOversizedFilesAreRejectedBeforeCallingTheAi() throws Exception {
        String token = registerCandidate("cv-invalid@test.com");

        upload(token, new MockMultipartFile("file", "cv.pdf", "application/pdf", new byte[0]))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Le fichier est vide."));
        upload(token, new MockMultipartFile("other", "cv.pdf", "application/pdf", PDF))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Aucun fichier reçu : sélectionnez un CV au format PDF."));
        byte[] oversized = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy(PDF, 0, oversized, 0, PDF.length);
        upload(token, new MockMultipartFile("file", "cv.pdf", "application/pdf", oversized))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.status").value(413))
                .andExpect(jsonPath("$.message").value("Le fichier dépasse la taille maximale de 5 Mo."));

        assertThat(AI.requestCount()).isZero();
        assertThat(storedFiles()).isEmpty();
        getAs(CV_URL, token).andExpect(status().isNotFound());
    }

    @Test
    void onlyTheConnectedCandidateCanManageACv() throws Exception {
        String owner = registerCandidate("cv-owner@test.com");
        upload(owner, pdf()).andExpect(status().isOk());
        AI.reset();

        getAs(CV_URL, null).andExpect(status().isUnauthorized());
        upload(null, pdf()).andExpect(status().isUnauthorized());
        upload(registerRecruiter("cv-recruiter@test.com"), pdf()).andExpect(status().isForbidden());
        getAs(CV_URL, adminToken()).andExpect(status().isForbidden());
        String other = registerCandidate("cv-other@test.com");
        getAs(CV_URL, other).andExpect(status().isNotFound());
        deleteAs(CV_URL, other).andExpect(status().isNotFound());

        assertThat(AI.requestCount()).isZero();
        assertThat(cvOf("cv-owner@test.com")).isNotNull();
    }

    @Test
    void pdfRejectedByTheParserKeepsThePreviousCv() throws Exception {
        String token = registerCandidate("cv-reject@test.com");
        upload(token, pdf()).andExpect(status().isOk());
        String previousFile = cvOf("cv-reject@test.com").getStoredFileName();

        AI.respond(422, """
                {"status":422,"error":"Unprocessable Content",
                 "message":"Le PDF ne contient pas de texte exploitable (document scanné ou image)."}
                """);
        upload(token, new MockMultipartFile("file", "scan.pdf", "application/pdf", PDF))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message")
                        .value("Le PDF ne contient pas de texte exploitable (document scanné ou image)."));

        Cv cv = cvOf("cv-reject@test.com");
        assertThat(cv.getStoredFileName()).isEqualTo(previousFile);
        assertThat(cv.getOriginalFileName()).isEqualTo("cv.pdf");
        assertThat(cv.getParsingStatus()).isEqualTo(CvParsingStatus.TERMINE);
        assertThat(storedFiles()).extracting(p -> p.getFileName().toString()).containsExactly(previousFile);
    }

    @Test
    void unavailableAiKeepsTheFileAndAllowsARetry() throws Exception {
        String token = registerCandidate("cv-retry@test.com");
        AI.respond(500, "{\"message\":\"Traceback interne\"}");

        upload(token, pdf())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parsingStatus").value("ECHEC"))
                .andExpect(jsonPath("$.parsingError").value(UNAVAILABLE))
                .andExpect(jsonPath("$.analysis").doesNotExist());
        assertThat(cvOf("cv-retry@test.com").getExtractedSkills()).isEmpty();
        assertThat(storedFiles()).hasSize(1);

        AI.respond(200, PARSE_RESULT);
        mvc.perform(post(CV_URL + "/analyze").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parsingStatus").value("TERMINE"))
                .andExpect(jsonPath("$.parsingError").doesNotExist())
                .andExpect(jsonPath("$.analysis.technologies", hasSize(4)));
        assertThat(AI.lastBodyBytes()).isEqualTo(PDF);
    }

    @Test
    void slowAiIsReportedAsTimeout() throws Exception {
        String token = registerCandidate("cv-timeout@test.com");
        AI.delay(3_000);

        upload(token, pdf())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parsingStatus").value("ECHEC"))
                .andExpect(jsonPath("$.parsingError").value(TIMEOUT));
    }

    @Test
    void invalidAiResultIsNeitherStoredNorDisplayed() throws Exception {
        String token = registerCandidate("cv-badresult@test.com");
        AI.respond(200, PARSE_RESULT.replace("\"level\": \"NATIF\"", "\"level\": \"EXPERT\""));

        upload(token, pdf())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parsingStatus").value("ECHEC"))
                .andExpect(jsonPath("$.parsingError").value(UNAVAILABLE))
                .andExpect(jsonPath("$.analysis").doesNotExist());

        AI.respond(200, PARSE_RESULT.replace("\"name\": \"Java\"", "\"name\": \"\""));
        upload(token, pdf()).andExpect(jsonPath("$.parsingStatus").value("ECHEC"));
        assertThat(cvOf("cv-badresult@test.com").getExtractedData()).isNull();
    }

    @Test
    void replacingTheCvUpdatesItInPlaceAndDeletesTheOldFile() throws Exception {
        String token = registerCandidate("cv-replace@test.com");
        upload(token, pdf()).andExpect(status().isOk());
        Cv first = cvOf("cv-replace@test.com");
        long id = first.getId();
        String firstFile = first.getStoredFileName();

        AI.respond(200, PARSE_RESULT.replace("""
                    {"name": "MySQL", "category": "Base de données", "occurrences": 1}""", """
                    {"name": "PostgreSQL", "category": "Base de données", "occurrences": 1}"""));
        upload(token, new MockMultipartFile("file", "cv-2026.pdf", "application/pdf", PDF))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.originalFileName").value("cv-2026.pdf"));

        Cv replaced = cvOf("cv-replace@test.com");
        assertThat(replaced.getStoredFileName()).isNotEqualTo(firstFile);
        assertThat(replaced.getExtractedSkills()).extracting(Skill::getName).contains("PostgreSQL").doesNotContain("MySQL");
        assertThat(storedFiles()).extracting(p -> p.getFileName().toString())
                .containsExactly(replaced.getStoredFileName());
    }

    @Test
    void deletingTheCvRemovesTheRowAndTheFile() throws Exception {
        String token = registerCandidate("cv-delete@test.com");
        upload(token, pdf()).andExpect(status().isOk());

        deleteAs(CV_URL, token).andExpect(status().isNoContent());

        getAs(CV_URL, token).andExpect(status().isNotFound());
        assertThat(cvOf("cv-delete@test.com")).isNull();
        assertThat(storedFiles()).isEmpty();
        deleteAs(CV_URL, token).andExpect(status().isNotFound());
    }

    @Test
    void importAddsCompleteEntriesToTheProfileOnlyOnce() throws Exception {
        String token = registerCandidate("cv-import@test.com");
        upload(token, pdf()).andExpect(status().isOk());
        String all = "{\"skills\":true,\"educations\":true,\"experiences\":true}";

        postJson(CV_URL + "/import", token, all)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skillsAdded").value(6))
                .andExpect(jsonPath("$.educationsAdded").value(1))
                .andExpect(jsonPath("$.experiencesAdded").value(2))
                .andExpect(jsonPath("$.skipped").value(2))
                .andExpect(jsonPath("$.educationLevelSet").value(true))
                .andExpect(jsonPath("$.yearsOfExperienceSet").value(true))
                .andExpect(jsonPath("$.profile.educationLevel").value("BAC_PLUS_5"))
                .andExpect(jsonPath("$.profile.yearsOfExperience").value(4))
                .andExpect(jsonPath("$.profile.experiences[0].jobTitle").value("Développeuse Full Stack"))
                .andExpect(jsonPath("$.profile.experiences[0].endDate").doesNotExist());

        CandidateProfile profile = profileOf("cv-import@test.com");
        assertThat(profile.getEducations()).singleElement().satisfies(e -> {
            assertThat(e.getInstitution()).isEqualTo("École Supérieure Fictive");
            assertThat(e.getStartDate()).isEqualTo(LocalDate.of(2019, 1, 1));
        });
        assertThat(profile.getExperiences()).extracting(e -> e.getStartDate().toString())
                .containsExactlyInAnyOrder("2023-03-01", "2022-02-01");

        postJson(CV_URL + "/import", token, all)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skillsAdded").value(0))
                .andExpect(jsonPath("$.educationsAdded").value(0))
                .andExpect(jsonPath("$.experiencesAdded").value(0))
                .andExpect(jsonPath("$.educationLevelSet").value(false));
    }

    @Test
    void importRespectsTheCandidateChoicesAndRequiresAnAnalysis() throws Exception {
        String token = registerCandidate("cv-import-choice@test.com");
        postJson(CV_URL + "/import", token, "{\"skills\":true}").andExpect(status().isNotFound());

        AI.respond(503, "");
        upload(token, pdf()).andExpect(jsonPath("$.parsingStatus").value("ECHEC"));
        postJson(CV_URL + "/import", token, "{\"skills\":true}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Le CV n'a pas encore été analysé : relancez l'analyse avant l'import."));

        AI.respond(200, PARSE_RESULT);
        upload(token, pdf()).andExpect(status().isOk());
        postJson(CV_URL + "/import", token, "{\"skills\":true,\"educations\":false,\"experiences\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skillsAdded").value(6))
                .andExpect(jsonPath("$.profile.educations", hasSize(0)))
                .andExpect(jsonPath("$.profile.experiences", hasSize(0)))
                .andExpect(jsonPath("$.profile.educationLevel").doesNotExist());
    }

    private ResultActions upload(String token, MockMultipartFile file) throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart(CV_URL).file(file);
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mvc.perform(request.contentType(MediaType.MULTIPART_FORM_DATA));
    }

    private static MockMultipartFile pdf() {
        return new MockMultipartFile("file", "cv.pdf", "application/pdf", PDF);
    }

    private CandidateProfile profileOf(String email) {
        long userId = userRepository.findByEmailIgnoreCase(email).orElseThrow().getId();
        return profileRepository.findByUserId(userId).orElseThrow();
    }

    private Cv cvOf(String email) {
        return profileOf(email).getCv();
    }

    private static List<Path> storedFiles() throws IOException {
        Path dir = UPLOADS.resolve("cvs");
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.toList();
        }
    }

    private static Path createUploadDir() {
        try {
            Path dir = Files.createTempDirectory("smartrecruit-cv-test");
            dir.toFile().deleteOnExit();
            return dir;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String resource(String path) {
        try (InputStream in = CandidateCvApiTest.class.getResourceAsStream(path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
