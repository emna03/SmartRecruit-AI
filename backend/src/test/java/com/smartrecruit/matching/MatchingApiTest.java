package com.smartrecruit.matching;

import com.smartrecruit.candidate.repository.CandidateProfileRepository;
import com.smartrecruit.matching.entity.MatchingResult;
import com.smartrecruit.matching.repository.MatchingResultRepository;
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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Matching dans Spring : CV analysé + offre -> service IA (simulé) -> MySQL (H2) -> API candidat et recruteur.
 * Les scores simulés reprennent des valeurs réellement produites par le module matching de FastAPI.
 */
class MatchingApiTest extends ApiTestSupport {

    private static final StubAiServer AI = StubAiServer.start();
    private static final Path UPLOADS = createUploadDir();
    private static final String PARSE = "/api/cv/parse";
    private static final String SCORE = "/api/matching/score";
    private static final String SEMANTIC = "/api/ml/semantic-matching";
    private static final byte[] PDF = "%PDF-1.4\n% CV fictif de test\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);
    private static final String PARSE_RESULT = resource("/cv/parse-result.json");
    private static final String SEMANTIC_OK = """
            {"semanticSimilarity":0.84,"semanticScore":84,"model":"sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2","version":"1.0.0","provider":"sentence-transformers"}
            """;

    /** Offre Java, Spring Boot, Docker (2 ans, Bac+5) et CV de test (Java, Spring Boot, 50 mois, Bac+5). */
    private static final String HIGH = result(80.0, 66.7, 100.0, 100.0, 3,
            matched("Java", "Java", "EXACT", 1.0) + "," + matched("Spring Boot", "Spring Boot", "EXACT", 1.0),
            "\"Docker\"");

    @Autowired
    private MatchingResultRepository matchingResultRepository;

    @Autowired
    private CandidateProfileRepository profileRepository;

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
    void reset() {
        AI.reset();
        AI.respondTo(PARSE, 200, PARSE_RESULT);
        AI.respondTo(SCORE, 200, HIGH);
        AI.respondTo(SEMANTIC, 200, SEMANTIC_OK);
    }

    @Test
    void candidateWithAnalyzedCvSeesExplainedScoreStoredInDatabase() throws Exception {
        String bob = recruiterWithCompany("bob@test.com", "Acme");
        long offer = createOffer(bob, "Dev Java", "PUBLIEE", "Java", "Spring Boot", "Docker");
        String alice = candidateWithCv("alice@test.com");

        matching(alice, offer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CALCULE"))
                .andExpect(jsonPath("$.message").value(containsString("pas d'une décision de recrutement")))
                .andExpect(jsonPath("$.result.score").value(80.0))
                .andExpect(jsonPath("$.result.skillsScore").value(66.7))
                .andExpect(jsonPath("$.result.experienceScore").value(100.0))
                .andExpect(jsonPath("$.result.educationScore").value(100.0))
                .andExpect(jsonPath("$.result.matchedSkills[*].name").value(contains("Java", "Spring Boot")))
                .andExpect(jsonPath("$.result.matchedSkills[0].matchType").value("EXACT"))
                .andExpect(jsonPath("$.result.missingSkills").value(contains("Docker")))
                .andExpect(jsonPath("$.result.algorithmVersion").value("1.0.0"))
                .andExpect(jsonPath("$.result.explanation").value(containsString("Score global")))
                .andExpect(jsonPath("$.result.stale").value(false))
                .andExpect(jsonPath("$.result.semanticScore").value(84))
                .andExpect(jsonPath("$.result.semanticSimilarity").value(0.84));

        assertThat(AI.requestCount(SCORE)).isGreaterThanOrEqualTo(1);
        assertThat(AI.requestCount(SEMANTIC)).isGreaterThanOrEqualTo(1);
        String sent = AI.lastBodyFor(SCORE);
        assertThat(sent).contains("\"skills\":[\"Java\",\"Spring Boot\",\"Angular\",\"MySQL\",\"API REST\",\"Scrum\"]")
                .contains("\"experienceMonths\":50", "\"educationLevel\":\"BAC_PLUS_5\"")
                .contains("\"requiredSkills\":[\"Docker\",\"Java\",\"Spring Boot\"]", "\"minExperienceYears\":2")
                .doesNotContain("alice@test.com", "Camille", "Tunis", "Test", "firstName", "text");
        assertThat(AI.lastBodyFor(SEMANTIC))
                .doesNotContain("alice@test.com", "Camille", "firstName", "email");

        MatchingResult stored = storedResult("alice@test.com", offer);
        assertThat(stored.getScore()).isEqualByComparingTo(new BigDecimal("80.00"));
        assertThat(stored.getSkillsScore()).isEqualByComparingTo(new BigDecimal("66.70"));
        assertThat(stored.getMatchedSkills()).contains("\"matchedBy\":\"Spring Boot\"", "\"matchType\":\"EXACT\"");
        assertThat(stored.getMissingSkills()).isEqualTo("[\"Docker\"]");
        assertThat(stored.getAlgorithmVersion()).isEqualTo("1.0.0");
        assertThat(stored.getComputedAt()).isNotNull();
        assertThat(stored.getSemanticScore()).isEqualByComparingTo(new BigDecimal("84.00"));
        assertThat(stored.getSemanticModel()).contains("sentence-transformers");

        AI.respondTo(SCORE, 200, result(90.0, 83.3, 100.0, 100.0, 3,
                matched("Java", "Java", "EXACT", 1.0) + "," + matched("Spring Boot", "Spring Boot", "EXACT", 1.0)
                        + "," + matched("Docker", "Kubernetes", "RELATED", 0.5), ""));
        matching(alice, offer).andExpect(jsonPath("$.result.score").value(90.0));
        assertThat(matchingResultRepository.findByJobOfferIdOrderByScoreDesc(offer)).singleElement()
                .satisfies(m -> assertThat(m.getScore()).isEqualByComparingTo("90.00"));
    }

    @Test
    void criteriaNotRequiredByTheOfferAreReturnedAsNull() throws Exception {
        String bob = recruiterWithCompany("bob@test.com", "Acme");
        long offer = createOffer(bob, "DevOps", "PUBLIEE", "Kubernetes", "Terraform");
        String alice = candidateWithCv("alice@test.com");
        AI.respondTo(SCORE, 200, result(0.0, 0.0, null, null, 2, "", "\"Kubernetes\",\"Terraform\""));

        matching(alice, offer)
                .andExpect(jsonPath("$.status").value("CALCULE"))
                .andExpect(jsonPath("$.result.score").value(0.0))
                .andExpect(jsonPath("$.result.experienceScore").doesNotExist())
                .andExpect(jsonPath("$.result.educationScore").doesNotExist())
                .andExpect(jsonPath("$.result.matchedSkills", hasSize(0)))
                .andExpect(jsonPath("$.result.missingSkills", hasSize(2)));
        MatchingResult stored = storedResult("alice@test.com", offer);
        assertThat(stored.getExperienceScore()).isNull();
        assertThat(stored.getEducationScore()).isNull();
    }

    @Test
    void candidateWithoutCvOrWithUnanalyzedCvGetsAClearStatusWithoutAiCall() throws Exception {
        String bob = recruiterWithCompany("bob@test.com", "Acme");
        long offer = createOffer(bob, "Dev Java", "PUBLIEE", "Java");
        String alice = registerCandidate("alice@test.com");

        matching(alice, offer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CV_ABSENT"))
                .andExpect(jsonPath("$.message").value(containsString("Importez votre CV")))
                .andExpect(jsonPath("$.result").doesNotExist());

        AI.respondTo(PARSE, 503, "");
        upload(alice).andExpect(jsonPath("$.parsingStatus").value("ECHEC"));
        matching(alice, offer)
                .andExpect(jsonPath("$.status").value("CV_NON_ANALYSE"))
                .andExpect(jsonPath("$.message").value(containsString("pas encore été analysé")));

        assertThat(AI.requestCount(SCORE)).isZero();
        assertThat(matchingResultRepository.findByJobOfferIdOrderByScoreDesc(offer)).isEmpty();
    }

    @Test
    void unavailableAiServiceReturnsLastStoredScoreWhenThereIsOne() throws Exception {
        String bob = recruiterWithCompany("bob@test.com", "Acme");
        long offer = createOffer(bob, "Dev Java", "PUBLIEE", "Java", "Spring Boot", "Docker");
        long other = createOffer(bob, "Dev Python", "PUBLIEE", "Python");
        String alice = candidateWithCv("alice@test.com");
        matching(alice, offer).andExpect(jsonPath("$.status").value("CALCULE"));

        AI.respondTo(SCORE, 503, "");
        matching(alice, offer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IA_INDISPONIBLE"))
                .andExpect(jsonPath("$.message").value(containsString("dernier score enregistré")))
                .andExpect(jsonPath("$.result.score").value(80.0));
        matching(alice, other)
                .andExpect(jsonPath("$.status").value("IA_INDISPONIBLE"))
                .andExpect(jsonPath("$.result").doesNotExist());

        AI.respondTo(SCORE, 200, HIGH);
        matching(alice, other).andExpect(jsonPath("$.status").value("CALCULE"));
    }

    @Test
    void invalidAiResponsesAreRejectedAndNeverStored() throws Exception {
        String bob = recruiterWithCompany("bob@test.com", "Acme");
        long offer = createOffer(bob, "Dev Java", "PUBLIEE", "Java", "Spring Boot", "Docker");
        String alice = candidateWithCv("alice@test.com");

        String[] invalid = {
                result(150.0, 66.7, 100.0, 100.0, 3, matched("Java", "Java", "EXACT", 1.0)
                        + "," + matched("Spring Boot", "Spring Boot", "EXACT", 1.0), "\"Docker\""),
                result(80.0, 66.7, 100.0, 100.0, 3, matched("Java", "Java", "EXACT", 1.0), "\"Docker\""),
                result(80.0, 66.7, 100.0, 100.0, 1, matched("Java", "Java", "MAGIC", 1.0), ""),
                "{\"score\":80}",
                "pas du json"
        };
        for (String body : invalid) {
            AI.respondTo(SCORE, 200, body);
            matching(alice, offer).andExpect(jsonPath("$.status").value("IA_INDISPONIBLE"));
        }
        assertThat(matchingResultRepository.findByJobOfferIdOrderByScoreDesc(offer)).isEmpty();
    }

    @Test
    void scoreIsOnlyAvailableForVisibleOffersAndToCandidates() throws Exception {
        String bob = recruiterWithCompany("bob@test.com", "Acme");
        long draft = createOffer(bob, "Brouillon", "BROUILLON", "Java");
        long offer = createOffer(bob, "Dev Java", "PUBLIEE", "Java");
        String alice = candidateWithCv("alice@test.com");

        matching(alice, draft).andExpect(status().isNotFound());
        matching(alice, 999_999L).andExpect(status().isNotFound());
        matching(bob, offer).andExpect(status().isForbidden());
        mvc.perform(get("/api/candidate/jobs/" + offer + "/matching")).andExpect(status().isUnauthorized());
        postJson("/api/recruiter/jobs/" + offer + "/matching", alice, "{}").andExpect(status().isForbidden());

        postJson("/api/candidate/applications", alice, "{\"jobOfferId\":%d}".formatted(offer))
                .andExpect(status().isCreated());
        patchJson("/api/recruiter/jobs/" + offer + "/status", bob, "{\"status\":\"FERMEE\"}")
                .andExpect(status().isOk());
        matching(alice, offer).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CALCULE"));
    }

    @Test
    void recruiterSeesApplicantsRankedFromBestToWorstScore() throws Exception {
        String bob = recruiterWithCompany("bob@test.com", "Acme");
        long offer = createOffer(bob, "Dev Java", "PUBLIEE", "Java", "Spring Boot", "Docker");

        apply(candidateWithCv("low@test.com"), offer, 19.0);
        apply(candidateWithCv("high@test.com"), offer, 90.0);
        apply(registerCandidate("nocv@test.com"), offer, null);
        apply(candidateWithCv("medium@test.com"), offer, 55.0);

        getAs("/api/recruiter/jobs/" + offer + "/applications", bob)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[*].candidate.email")
                        .value(contains("high@test.com", "medium@test.com", "low@test.com", "nocv@test.com")))
                .andExpect(jsonPath("$[0].matching.score").value(90.0))
                .andExpect(jsonPath("$[0].matching.missingSkills").value(contains("Docker")))
                .andExpect(jsonPath("$[0].candidate.cvAnalyzed").value(true))
                .andExpect(jsonPath("$[3].matching").doesNotExist())
                .andExpect(jsonPath("$[3].candidate.cvAnalyzed").value(false));

        long highApplication = applicationOf(bob, offer, "high@test.com");
        getAs("/api/recruiter/applications/" + highApplication, bob)
                .andExpect(jsonPath("$.matching.score").value(90.0))
                .andExpect(jsonPath("$.matching.explanation").value(containsString("Score global")));
        patchJson("/api/recruiter/applications/" + highApplication + "/status", bob, "{\"status\":\"PRESELECTIONNE\"}")
                .andExpect(jsonPath("$.matching.score").value(90.0));
    }

    @Test
    void recruiterRecomputesScoresOfAllApplicantsWithAnAnalyzedCv() throws Exception {
        String bob = recruiterWithCompany("bob@test.com", "Acme");
        String eve = recruiterWithCompany("eve@test.com", "Globex");
        long offer = createOffer(bob, "Dev Java", "PUBLIEE", "Java", "Spring Boot", "Docker");
        AI.respondTo(SCORE, 503, "");
        apply(candidateWithCv("alice@test.com"), offer, null);
        apply(candidateWithCv("carl@test.com"), offer, null);
        apply(registerCandidate("nocv@test.com"), offer, null);
        assertThat(matchingResultRepository.findByJobOfferIdOrderByScoreDesc(offer)).isEmpty();

        String refresh = "/api/recruiter/jobs/" + offer + "/matching";
        postJson(refresh, bob, "{}").andExpect(status().isServiceUnavailable());
        postJson(refresh, eve, "{}").andExpect(status().isNotFound());

        AI.respondTo(SCORE, 200, HIGH);
        postJson(refresh, bob, "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.computed").value(2))
                .andExpect(jsonPath("$.withoutAnalyzedCv").value(1))
                .andExpect(jsonPath("$.applications", hasSize(3)))
                .andExpect(jsonPath("$.applications[0].matching.score").value(80.0))
                .andExpect(jsonPath("$.applications[2].candidate.email").value("nocv@test.com"));
        assertThat(matchingResultRepository.findByJobOfferIdOrderByScoreDesc(offer)).hasSize(2);
    }

    @Test
    void applyingStillWorksWhenScoreCannotBeComputed() throws Exception {
        String bob = recruiterWithCompany("bob@test.com", "Acme");
        long offer = createOffer(bob, "Dev Java", "PUBLIEE", "Java");
        String alice = candidateWithCv("alice@test.com");
        AI.respondTo(SCORE, 503, "");

        postJson("/api/candidate/applications", alice, "{\"jobOfferId\":%d}".formatted(offer))
                .andExpect(status().isCreated());
        assertThat(AI.requestCount(SCORE)).isEqualTo(1);
        assertThat(matchingResultRepository.findByJobOfferIdOrderByScoreDesc(offer)).isEmpty();
    }

    @Test
    void scoresAreInvalidatedWhenTheCvOrTheOfferCriteriaChange() throws Exception {
        String bob = recruiterWithCompany("bob@test.com", "Acme");
        long offer = createOffer(bob, "Dev Java", "PUBLIEE", "Java", "Spring Boot", "Docker");
        String alice = candidateWithCv("alice@test.com");
        apply(alice, offer, 80.0);

        upload(alice).andExpect(status().isOk());
        getAs("/api/recruiter/jobs/" + offer + "/applications", bob)
                .andExpect(jsonPath("$[0].matching.stale").value(true));
        matching(alice, offer).andExpect(jsonPath("$.result.stale").value(false));

        putJson("/api/recruiter/jobs/" + offer, bob, offerJson("Dev Java senior", 2, "Java", "Spring Boot", "Docker"))
                .andExpect(status().isOk());
        assertThat(matchingResultRepository.findByJobOfferIdOrderByScoreDesc(offer)).hasSize(1);

        putJson("/api/recruiter/jobs/" + offer, bob, offerJson("Dev Java senior", 5, "Java", "Spring Boot", "Docker"))
                .andExpect(status().isOk());
        assertThat(matchingResultRepository.findByJobOfferIdOrderByScoreDesc(offer)).isEmpty();

        matching(alice, offer).andExpect(jsonPath("$.status").value("CALCULE"));
        deleteAs("/api/candidate/cv", alice).andExpect(status().isNoContent());
        assertThat(matchingResultRepository.findByJobOfferIdOrderByScoreDesc(offer)).isEmpty();
        matching(alice, offer).andExpect(jsonPath("$.status").value("CV_ABSENT"));
    }

    @Test
    void deletingAnOfferOrACandidateRemovesTheirScores() throws Exception {
        String bob = recruiterWithCompany("bob@test.com", "Acme");
        long offer = createOffer(bob, "Dev Java", "PUBLIEE", "Java", "Spring Boot", "Docker");
        apply(candidateWithCv("alice@test.com"), offer, 80.0);
        assertThat(matchingResultRepository.count()).isEqualTo(1);

        deleteAs("/api/recruiter/jobs/" + offer, bob).andExpect(status().isNoContent());
        matchingResultRepository.flush();
        assertThat(matchingResultRepository.count()).isZero();

        long other = createOffer(bob, "Dev Java 2", "PUBLIEE", "Java", "Spring Boot", "Docker");
        matching(candidateWithCv("carl@test.com"), other).andExpect(jsonPath("$.status").value("CALCULE"));
        assertThat(matchingResultRepository.count()).isEqualTo(1);
        deleteAs("/api/admin/users/" + userRepository.findByEmailIgnoreCase("carl@test.com").orElseThrow().getId(),
                adminToken()).andExpect(status().isNoContent());
        matchingResultRepository.flush();
        assertThat(matchingResultRepository.count()).isZero();
    }

    // ---------- Utilitaires ----------

    private String candidateWithCv(String email) throws Exception {
        String token = registerCandidate(email);
        upload(token).andExpect(status().isOk()).andExpect(jsonPath("$.parsingStatus").value("TERMINE"));
        return token;
    }

    /** Postule ; le score renvoyé par le service IA simulé est fixé juste avant (null : pas de changement). */
    private void apply(String token, long offer, Double score) throws Exception {
        if (score != null) {
            AI.respondTo(SCORE, 200, result(score, 66.7, 100.0, 100.0, 3,
                    matched("Java", "Java", "EXACT", 1.0) + "," + matched("Spring Boot", "Spring Boot", "EXACT", 1.0),
                    "\"Docker\""));
        }
        postJson("/api/candidate/applications", token, "{\"jobOfferId\":%d}".formatted(offer))
                .andExpect(status().isCreated());
    }

    private long applicationOf(String recruiter, long offer, String email) throws Exception {
        String json = getAs("/api/recruiter/jobs/" + offer + "/applications", recruiter)
                .andReturn().getResponse().getContentAsString();
        java.util.List<Number> ids = com.jayway.jsonpath.JsonPath.read(json,
                "$[?(@.candidate.email == '" + email + "')].id");
        return ids.getFirst().longValue();
    }

    private ResultActions matching(String token, long offer) throws Exception {
        return getAs("/api/candidate/jobs/" + offer + "/matching", token);
    }

    private ResultActions upload(String token) throws Exception {
        return mvc.perform(multipart("/api/candidate/cv")
                .file(new MockMultipartFile("file", "cv.pdf", "application/pdf", PDF))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.MULTIPART_FORM_DATA));
    }

    private MatchingResult storedResult(String email, long offer) {
        long userId = userRepository.findByEmailIgnoreCase(email).orElseThrow().getId();
        long candidateId = profileRepository.findByUserId(userId).orElseThrow().getId();
        return matchingResultRepository.findByCandidateIdAndJobOfferId(candidateId, offer).orElseThrow();
    }

    private static String offerJson(String title, int years, String... skills) {
        String skillsJson = Arrays.stream(skills).map(s -> "\"" + s + "\"").collect(Collectors.joining(","));
        return """
                {"title":"%s","description":"Poste de %s","location":"Tunis","contractType":"CDI",
                 "minExperienceYears":%d,"educationLevel":"BAC_PLUS_5","requiredSkills":[%s]}
                """.formatted(title, title, years, skillsJson);
    }

    private static String matched(String name, String by, String type, double credit) {
        return "{\"name\":\"%s\",\"matchedBy\":\"%s\",\"matchType\":\"%s\",\"credit\":%s}"
                .formatted(name, by, type, credit);
    }

    /** Réponse au format du module matching de FastAPI. */
    private static String result(double score, double skills, Double experience, Double education, int required,
                                 String matchedJson, String missingJson) {
        return """
                {"algorithmVersion":"1.0.0","score":%s,"skillsScore":%s,"experienceScore":%s,"educationScore":%s,
                 "weights":{"skills":0.6,"experience":0.25,"education":0.15},"requiredSkillsCount":%d,
                 "matchedSkills":[%s],"missingSkills":[%s],
                 "explanation":"Score global : %s %% — compatibilité. Ce score est une aide indicative."}
                """.formatted(score, skills, experience, education, required, matchedJson, missingJson, score);
    }

    private static Path createUploadDir() {
        try {
            Path dir = Files.createTempDirectory("smartrecruit-matching-test");
            dir.toFile().deleteOnExit();
            return dir;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String resource(String path) {
        try (InputStream in = MatchingApiTest.class.getResourceAsStream(path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
