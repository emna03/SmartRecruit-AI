package com.smartrecruit.chatbot;

import com.smartrecruit.support.ApiTestSupport;
import com.smartrecruit.support.StubAiServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Chatbot dans Spring : question -> catalogue des offres publiées -> service IA (simulé) -> réponse validée.
 * Les réponses simulées reprennent le format et des textes réellement produits par le module chatbot de FastAPI.
 */
class ChatbotApiTest extends ApiTestSupport {

    private static final StubAiServer AI = StubAiServer.start();
    private static final String ASK = "/api/chatbot/ask";
    private static final String URL = "/api/candidate/chatbot/messages";

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
    void candidateGetsAnAnswerBuiltFromPublishedOffersOnly() throws Exception {
        String bob = recruiterWithCompany("bob@test.com", "Acme");
        long java = createOffer(bob, "Dev Java", "PUBLIEE", "Spring Boot", "Java");
        long angular = createOffer(bob, "Front Angular", "PUBLIEE", "Angular");
        createOffer(bob, "Projet confidentiel", "BROUILLON", "Java");
        String alice = registerCandidate("alice@test.com");
        AI.respondTo(ASK, 200, reply("SEARCH_OFFERS", "J'ai trouvé 1 offre publiée qui demande Java.",
                "[" + java + "]", 1, java, "[\"Java\"]", "[]"));

        ask(alice, "Quelles offres correspondent à Java ?", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("SEARCH_OFFERS"))
                .andExpect(jsonPath("$.answer").value("J'ai trouvé 1 offre publiée qui demande Java."))
                .andExpect(jsonPath("$.confidence").value(1.0))
                .andExpect(jsonPath("$.offers", hasSize(1)))
                .andExpect(jsonPath("$.offers[0].id").value(java))
                .andExpect(jsonPath("$.offers[0].title").value("Dev Java"))
                .andExpect(jsonPath("$.offers[0].companyName").value("Acme"))
                .andExpect(jsonPath("$.offers[0].location").value("Tunis"))
                .andExpect(jsonPath("$.offers[0].contractType").value("CDI"))
                .andExpect(jsonPath("$.offers[0].minExperienceYears").value(2))
                .andExpect(jsonPath("$.offers[0].educationLevel").value("BAC_PLUS_5"))
                .andExpect(jsonPath("$.offers[0].requiredSkills").value(contains("Java", "Spring Boot")))
                .andExpect(jsonPath("$.totalMatches").value(1))
                .andExpect(jsonPath("$.contextOffer.id").value(java))
                .andExpect(jsonPath("$.entities.skills").value(contains("Java")))
                .andExpect(jsonPath("$.suggestions", hasSize(1)))
                .andExpect(jsonPath("$.chatbotVersion").value("1.0.0"));

        String sent = AI.lastBody();
        assertThat(AI.lastPath()).isEqualTo(ASK);
        assertThat(AI.lastHeader("X-Internal-Api-Key")).isNotBlank();
        assertThat(sent).contains("\"message\":\"Quelles offres correspondent à Java ?\"", "\"contextOfferId\":null",
                        "\"id\":" + java, "\"id\":" + angular, "\"title\":\"Dev Java\"", "\"company\":\"Acme\"",
                        "\"requiredSkills\":[\"Java\",\"Spring Boot\"]", "\"minExperienceYears\":2")
                .doesNotContain("Projet confidentiel", "alice@test.com", "bob@test.com", "email", "firstName",
                        "description", "Poste de");
    }

    @Test
    void offerQuestionUsesThePublishedContextOfferAndReturnsItFromTheDatabase() throws Exception {
        String bob = recruiterWithCompany("bob@test.com", "Acme");
        long java = createOffer(bob, "Dev Java", "PUBLIEE", "Java");
        String alice = registerCandidate("alice@test.com");
        AI.respondTo(ASK, 200, reply("OFFER_EXPERIENCE",
                "L'offre « Dev Java » (Acme) demande au moins 2 ans d'expérience.", "[" + java + "]", 1, java,
                "[]", "[]"));

        ask(alice, "Combien d'années d'expérience sont nécessaires ?", java)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("OFFER_EXPERIENCE"))
                .andExpect(jsonPath("$.answer").value(containsString("au moins 2 ans")))
                .andExpect(jsonPath("$.contextOffer.title").value("Dev Java"))
                .andExpect(jsonPath("$.offers[0].id").value(java));

        assertThat(AI.lastBody()).contains("\"contextOfferId\":" + java);
    }

    @Test
    void draftClosedOrMissingContextOfferIsNotSent() throws Exception {
        String bob = recruiterWithCompany("bob@test.com", "Acme");
        createOffer(bob, "Dev Java", "PUBLIEE", "Java");
        long draft = createOffer(bob, "Brouillon", "BROUILLON", "Java");
        long closed = createOffer(bob, "Fermée", "FERMEE", "Java");
        String alice = registerCandidate("alice@test.com");
        AI.respondTo(ASK, 200, reply("OFFER_SKILLS", "De quelle offre parlez-vous ?", "[]", 0, null, "[]", "[]"));

        for (long id : new long[]{draft, closed, 999_999L}) {
            ask(alice, "Quelles sont les compétences demandées pour cette offre ?", id)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contextOffer").doesNotExist());
            assertThat(AI.lastBody()).contains("\"contextOfferId\":null").doesNotContain("Brouillon", "Fermée");
        }
    }

    @Test
    void platformQuestionIsAnsweredWithActionsAndWithoutOffers() throws Exception {
        String alice = registerCandidate("alice@test.com");
        AI.respondTo(ASK, 200, reply("FAQ_CV", "Allez dans « Mon CV », puis glissez votre fichier.", "[]", 0, null,
                "[]", "[\"OPEN_CV\"]"));

        ask(alice, "Comment importer mon CV ?", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("FAQ_CV"))
                .andExpect(jsonPath("$.offers", hasSize(0)))
                .andExpect(jsonPath("$.actions").value(contains("OPEN_CV")));
        assertThat(AI.lastBody()).contains("\"offers\":[]");
    }

    @Test
    void answerCitingAnOfferOutsideTheCatalogueIsRejected() throws Exception {
        String bob = recruiterWithCompany("bob@test.com", "Acme");
        long java = createOffer(bob, "Dev Java", "PUBLIEE", "Java");
        long draft = createOffer(bob, "Brouillon", "BROUILLON", "Java");
        String alice = registerCandidate("alice@test.com");

        for (String invented : new String[]{
                reply("SEARCH_OFFERS", "Une offre inventée.", "[999999]", 1, null, "[]", "[]"),
                reply("SEARCH_OFFERS", "Un brouillon.", "[" + draft + "]", 1, null, "[]", "[]"),
                reply("OFFER_SKILLS", "Contexte inventé.", "[]", 0, 999_999L, "[]", "[]"),
                reply("SEARCH_OFFERS", "Doublon.", "[" + java + "," + java + "]", 2, null, "[]", "[]")}) {
            AI.respondTo(ASK, 200, invented);
            ask(alice, "Quelles offres correspondent à Java ?", null)
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.message").value(containsString("indisponible")));
        }
    }

    @Test
    void invalidAiResponseIsNeverShown() throws Exception {
        String alice = registerCandidate("alice@test.com");

        for (String invalid : new String[]{
                reply("INVENTED_INTENT", "Réponse.", "[]", 0, null, "[]", "[]"),
                reply("UNKNOWN", "", "[]", 0, null, "[]", "[]"),
                reply("UNKNOWN", "x".repeat(2001), "[]", 0, null, "[]", "[]"),
                reply("UNKNOWN", "Réponse.", "[]", 0, null, "[]", "[\"DELETE_ACCOUNT\"]"),
                reply("UNKNOWN", "Réponse.", "[]", 0, null, "[]", "[]").replace("\"confidence\":1.0",
                        "\"confidence\":2.5"),
                "{}", "not json"}) {
            AI.respondTo(ASK, 200, invalid);
            ask(alice, "Bonjour", null).andExpect(status().isServiceUnavailable());
        }
    }

    @Test
    void unavailableAiServiceGivesAClear503() throws Exception {
        String alice = registerCandidate("alice@test.com");
        AI.respondTo(ASK, 500, "{\"message\":\"boom\"}");

        ask(alice, "Comment postuler à une offre ?", null)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Le service IA est momentanément indisponible. Réessayez plus tard."));
    }

    @Test
    void emptyTooLongOrMalformedQuestionsAreRefusedWithoutCallingTheAi() throws Exception {
        String alice = registerCandidate("alice@test.com");

        ask(alice, "", null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.message").value("La question ne peut pas être vide"));
        ask(alice, "   ", null).andExpect(status().isBadRequest());
        ask(alice, "a".repeat(501), null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.message").value("La question ne doit pas dépasser 500 caractères"));
        ask(alice, "Bonjour", -1L).andExpect(status().isBadRequest());
        postJson(URL, alice, "{\"message\":").andExpect(status().isBadRequest());
        postJson(URL, alice, "{}").andExpect(status().isBadRequest());

        assertThat(AI.requestCount()).isZero();
    }

    @Test
    void onlyAuthenticatedCandidatesCanUseTheChatbot() throws Exception {
        String bob = registerRecruiter("bob@test.com");
        String admin = adminToken();

        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Bonjour\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(URL).header("Authorization", "Bearer invalid.token.value")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Bonjour\"}"))
                .andExpect(status().isUnauthorized());
        ask(bob, "Bonjour", null).andExpect(status().isForbidden());
        ask(admin, "Bonjour", null).andExpect(status().isForbidden());

        assertThat(AI.requestCount()).isZero();
    }

    // ---------- Utilitaires ----------

    private ResultActions ask(String token, String message, Long jobOfferId) throws Exception {
        String body = jobOfferId == null
                ? "{\"message\":\"%s\"}".formatted(message)
                : "{\"message\":\"%s\",\"jobOfferId\":%d}".formatted(message, jobOfferId);
        return postJson(URL, token, body);
    }

    /** Réponse au format du module chatbot de FastAPI. */
    private static String reply(String intent, String answer, String offerIdsJson, int total, Long contextOfferId,
                                String skillsJson, String actionsJson) {
        return """
                {"chatbotVersion":"1.0.0","intent":"%s","confidence":1.0,"answer":"%s","offerIds":%s,
                 "totalMatches":%d,"contextOfferId":%s,
                 "entities":{"skills":%s,"experienceYears":null,"educationLevel":null,"contractType":null,
                             "location":null,"keywords":[]},
                 "suggestions":["Comment postuler à une offre ?"],"actions":%s}
                """.formatted(intent, answer, offerIdsJson, total, contextOfferId, skillsJson, actionsJson);
    }
}
