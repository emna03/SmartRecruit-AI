package com.smartrecruit.turnover;

import com.smartrecruit.support.ApiTestSupport;
import com.smartrecruit.support.StubAiServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Risque de turnover dans Spring : saisie -> validation -> service IA (simulé) -> prédiction validée.
 * Les réponses simulées sont celles réellement produites par le modèle de FastAPI pour deux employés fictifs du
 * jeu de test du dataset IBM (exemples de ai-service/ml/reports/turnover_evaluation.json).
 */
class TurnoverPredictionApiTest extends ApiTestSupport {

    private static final StubAiServer AI = StubAiServer.start();
    private static final String PREDICT = "/api/ml/turnover/predict";
    private static final String URL = "/api/hr/turnover/predictions";
    private static final String HIGH_REQUEST = resource("/turnover/high-risk-request.json");
    private static final String HIGH_PREDICTION = resource("/turnover/high-risk-prediction.json");
    private static final String LOW_REQUEST = resource("/turnover/low-risk-request.json");
    private static final String LOW_PREDICTION = resource("/turnover/low-risk-prediction.json");
    private static final ObjectMapper JSON = new ObjectMapper();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.ai-service.url", AI::url);
    }

    @AfterAll
    static void stopAi() {
        AI.close();
    }

    @BeforeEach
    void reset() {
        AI.reset();
    }

    @Test
    void recruiterGetsAHighRiskPredictionWithItsFactorsAndTheRealModelMetrics() throws Exception {
        String bob = registerRecruiter("bob@test.com");
        AI.respondTo(PREDICT, 200, HIGH_PREDICTION);

        predict(bob, HIGH_REQUEST)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.probability").value(0.9166))
                .andExpect(jsonPath("$.riskLevel").value("ELEVE"))
                .andExpect(jsonPath("$.mediumThreshold").value(0.3))
                .andExpect(jsonPath("$.highThreshold").value(0.5))
                .andExpect(jsonPath("$.factors", hasSize(5)))
                .andExpect(jsonPath("$.factors[3].feature").value("overTime"))
                .andExpect(jsonPath("$.factors[3].label").value("Heures supplémentaires"))
                .andExpect(jsonPath("$.factors[3].direction").value("INCREASES_RISK"))
                .andExpect(jsonPath("$.factors[3].referenceValue").value("false"))
                .andExpect(jsonPath("$.model.name").value("RANDOM_FOREST"))
                .andExpect(jsonPath("$.model.dataset").value("IBM HR Analytics Employee Attrition & Performance"))
                .andExpect(jsonPath("$.model.testRocAuc").value(0.7844))
                .andExpect(jsonPath("$.riskLevelConsistent").doesNotExist());

        assertThat(AI.lastPath()).isEqualTo(PREDICT);
        assertThat(AI.lastHeader("X-Internal-Api-Key")).isNotBlank();
        assertThat(JSON.readTree(AI.lastBody())).isEqualTo(JSON.readTree(HIGH_REQUEST));
    }

    @Test
    void lowRiskPredictionIsReturned() throws Exception {
        String bob = registerRecruiter("bob@test.com");
        AI.respondTo(PREDICT, 200, LOW_PREDICTION);

        predict(bob, LOW_REQUEST)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.probability").value(0.0786))
                .andExpect(jsonPath("$.riskLevel").value("FAIBLE"))
                .andExpect(jsonPath("$.factors[0].direction").value("DECREASES_RISK"));
        assertThat(JSON.readTree(AI.lastBody())).isEqualTo(JSON.readTree(LOW_REQUEST));
    }

    @Test
    void adminCanAlsoUseThePrediction() throws Exception {
        AI.respondTo(PREDICT, 200, LOW_PREDICTION);

        predict(adminToken(), LOW_REQUEST).andExpect(status().isOk());
    }

    @Test
    void onlyTheModelVariablesAreSentToTheAiService() throws Exception {
        String bob = registerRecruiter("bob@test.com");
        AI.respondTo(PREDICT, 200, LOW_PREDICTION);
        String withPersonalData = with(LOW_REQUEST, Map.of("age", 35, "gender", "Female", "maritalStatus", "Single",
                "employeeName", "Alice Martin", "email", "alice@test.com"));

        predict(bob, withPersonalData).andExpect(status().isOk());

        assertThat(AI.lastBody()).doesNotContain("age\"", "gender", "maritalStatus", "Alice", "alice@test.com",
                "Consistent", "WithinCareer", "InDepartment");
        assertThat(JSON.readTree(AI.lastBody()).size()).isEqualTo(24);
    }

    @Test
    void missingDataIsRefusedWithoutCallingTheAi() throws Exception {
        String bob = registerRecruiter("bob@test.com");

        for (String field : new String[]{"monthlyIncome", "overTime", "jobRole", "yearsAtCompany", "department"}) {
            ObjectNode body = (ObjectNode) JSON.readTree(LOW_REQUEST);
            body.remove(field);
            predict(bob, body.toString())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Données invalides"))
                    .andExpect(jsonPath("$.fieldErrors." + field).exists());
        }
        predict(bob, "{}").andExpect(status().isBadRequest());
        assertThat(AI.requestCount()).isZero();
    }

    @Test
    void outOfRangeValuesAreRefusedWithoutCallingTheAi() throws Exception {
        String bob = registerRecruiter("bob@test.com");
        Object[][] cases = {
                {"monthlyIncome", 500}, {"monthlyIncome", 25000}, {"jobSatisfaction", 0}, {"jobSatisfaction", 5},
                {"performanceRating", 2}, {"distanceFromHome", -1}, {"stockOptionLevel", 4}, {"education", 6},
        };
        for (Object[] invalid : cases) {
            predict(bob, with(LOW_REQUEST, Map.of((String) invalid[0], invalid[1])))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors." + invalid[0]).exists());
        }
        assertThat(AI.requestCount()).isZero();
    }

    @Test
    void wronglyTypedValuesAreRefusedInsteadOfBeingConverted() throws Exception {
        String bob = registerRecruiter("bob@test.com");
        String[][] cases = {
                {"education", "2.5"}, {"monthlyIncome", "\"5000\""}, {"jobLevel", "true"}, {"overTime", "\"yes\""},
                {"overTime", "1"}, {"businessTravel", "\"SOMETIMES\""}, {"jobRole", "\"CEO\""},
        };
        for (String[] invalid : cases) {
            String body = LOW_REQUEST.replaceFirst("\"" + invalid[0] + "\":[^,}]+", "\"" + invalid[0] + "\":" + invalid[1]);
            assertThat(body).contains("\"" + invalid[0] + "\":" + invalid[1]);
            predict(bob, body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Requête mal formée"));
        }
        assertThat(AI.requestCount()).isZero();
    }

    @Test
    void inconsistentValuesAreRefusedWithoutCallingTheAi() throws Exception {
        String bob = registerRecruiter("bob@test.com");

        predict(bob, with(LOW_REQUEST, Map.of("totalWorkingYears", 3)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.yearsAtCompanyWithinCareer")
                        .value("L'ancienneté dans l'entreprise ne peut pas dépasser l'expérience totale"));
        for (String field : new String[]{"yearsInCurrentRole", "yearsWithCurrManager", "yearsSinceLastPromotion"}) {
            predict(bob, with(LOW_REQUEST, Map.of(field, 8)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.yearsWithinCompany").exists());
        }
        predict(bob, with(LOW_REQUEST, Map.of("department", "SALES")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.jobRoleInDepartment")
                        .value("Ce poste n'existe pas dans le département choisi"));
        assertThat(AI.requestCount()).isZero();
    }

    @Test
    void unavailableAiServiceOrModelGivesAClear503() throws Exception {
        String bob = registerRecruiter("bob@test.com");

        AI.respondTo(PREDICT, 500, "{\"message\":\"boom\"}");
        predict(bob, LOW_REQUEST)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Le service IA est momentanément indisponible. Réessayez plus tard."));

        AI.respondTo(PREDICT, 503, "{\"message\":\"Modèle de prédiction indisponible : il doit être entraîné.\"}");
        predict(bob, LOW_REQUEST).andExpect(status().isServiceUnavailable());
    }

    @Test
    void invalidAiResponsesAreNeverShown() throws Exception {
        String bob = registerRecruiter("bob@test.com");
        String[] invalid = {
                HIGH_PREDICTION.replace("\"probability\":0.9166", "\"probability\":1.5"),
                HIGH_PREDICTION.replace("\"riskLevel\":\"ELEVE\"", "\"riskLevel\":\"FAIBLE\""),
                HIGH_PREDICTION.replace("\"riskLevel\":\"ELEVE\"", "\"riskLevel\":\"CERTAIN\""),
                HIGH_PREDICTION.replace("\"mediumThreshold\":0.3", "\"mediumThreshold\":0.6"),
                HIGH_PREDICTION.replace("\"feature\":\"jobLevel\"", "\"feature\":\"age\""),
                HIGH_PREDICTION.replace("\"feature\":\"jobLevel\"", "\"feature\":\"overTime\""),
                HIGH_PREDICTION.replaceFirst("\"direction\":\"INCREASES_RISK\"", "\"direction\":\"DECREASES_RISK\""),
                HIGH_PREDICTION.replace("\"testRocAuc\":0.7844", "\"testRocAuc\":78.4"),
                HIGH_PREDICTION.replace("\"name\":\"RANDOM_FOREST\"", "\"name\":\"GPT\""),
                HIGH_PREDICTION.replace("\"factors\":[", "\"factors\":[" + "{\"feature\":\"education\",\"label\":\"x\","
                        + "\"impact\":0.01,\"direction\":\"INCREASES_RISK\"},"),
                "{}", "not json",
        };
        for (String body : invalid) {
            AI.respondTo(PREDICT, 200, body);
            predict(bob, HIGH_REQUEST)
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.message").value(containsString("indisponible")));
        }
    }

    @Test
    void onlyAuthenticatedAdminsAndRecruitersCanPredict() throws Exception {
        String alice = registerCandidate("alice@test.com");

        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(LOW_REQUEST))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(URL).header("Authorization", "Bearer invalid.token.value")
                        .contentType(MediaType.APPLICATION_JSON).content(LOW_REQUEST))
                .andExpect(status().isUnauthorized());
        predict(alice, LOW_REQUEST).andExpect(status().isForbidden());

        assertThat(AI.requestCount()).isZero();
    }

    // ---------- Utilitaires ----------

    private ResultActions predict(String token, String body) throws Exception {
        return postJson(URL, token, body);
    }

    private static String with(String json, Map<String, Object> changes) {
        ObjectNode node = (ObjectNode) JSON.readTree(json);
        changes.forEach((field, value) -> node.set(field, JSON.valueToTree(value)));
        return node.toString();
    }

    private static String resource(String path) {
        try (InputStream in = TurnoverPredictionApiTest.class.getResourceAsStream(path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
