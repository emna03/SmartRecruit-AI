package com.smartrecruit.application;

import com.smartrecruit.support.ApiTestSupport;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApplicationApiTest extends ApiTestSupport {

    @Test
    void candidateAppliesOnceToPublishedOffer() throws Exception {
        String bob = recruiterWithCompany("bob@acme.com", "Acme");
        long offer = createOffer(bob, "Dev Java", "PUBLIEE", "Java");
        String alice = registerCandidate("alice@test.com");

        postJson("/api/candidate/applications", alice, """
                {"jobOfferId":%d,"coverLetter":"Très motivée"}
                """.formatted(offer))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("EN_ATTENTE"))
                .andExpect(jsonPath("$.job.title").value("Dev Java"))
                .andExpect(jsonPath("$.job.companyName").value("Acme"));

        postJson("/api/candidate/applications", alice, """
                {"jobOfferId":%d}
                """.formatted(offer))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Vous avez déjà postulé à cette offre"));

        getAs("/api/candidate/applications", alice)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void cannotApplyToDraftClosedOrMissingOffer() throws Exception {
        String bob = recruiterWithCompany("bob@acme.com", "Acme");
        long draft = createOffer(bob, "Brouillon", "BROUILLON", "Java");
        long closed = createOffer(bob, "Fermée", "FERMEE", "Java");
        String alice = registerCandidate("alice@test.com");

        postJson("/api/candidate/applications", alice, "{\"jobOfferId\":%d}".formatted(draft))
                .andExpect(status().isBadRequest());
        postJson("/api/candidate/applications", alice, "{\"jobOfferId\":%d}".formatted(closed))
                .andExpect(status().isBadRequest());
        postJson("/api/candidate/applications", alice, "{\"jobOfferId\":999999}")
                .andExpect(status().isNotFound());
        postJson("/api/candidate/applications", alice, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.jobOfferId").exists());
    }

    @Test
    void recruiterSeesApplicantsAndUpdatesStatusVisibleToCandidate() throws Exception {
        String bob = recruiterWithCompany("bob@acme.com", "Acme");
        long offer = createOffer(bob, "Dev Java", "PUBLIEE", "Java");
        String alice = registerCandidate("alice@test.com");
        putJson("/api/candidate/profile/skills", alice, "{\"skills\":[\"Java\",\"Angular\"]}");
        long applicationId = readId(postJson("/api/candidate/applications", alice,
                "{\"jobOfferId\":%d}".formatted(offer)), "$.id");

        getAs("/api/recruiter/jobs/" + offer + "/applications", bob)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].candidate.email").value("alice@test.com"))
                .andExpect(jsonPath("$[0].candidate.skills.length()").value(2));

        getAs("/api/recruiter/applications/" + applicationId, bob)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidate.skills.length()").value(2));

        getAs("/api/recruiter/jobs/" + offer, bob).andExpect(jsonPath("$.applicationCount").value(1));

        patchJson("/api/recruiter/applications/" + applicationId + "/status", bob, """
                {"status":"PRESELECTIONNE"}
                """).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PRESELECTIONNE"));

        getAs("/api/candidate/applications/" + applicationId, alice)
                .andExpect(jsonPath("$.status").value("PRESELECTIONNE"));

        patchJson("/api/recruiter/applications/" + applicationId + "/status", bob, """
                {"status":"EMBAUCHE"}
                """).andExpect(status().isBadRequest());
    }

    @Test
    void applicationsAreIsolatedBetweenUsers() throws Exception {
        String bob = recruiterWithCompany("bob@acme.com", "Acme");
        String eve = recruiterWithCompany("eve@globex.com", "Globex");
        long offer = createOffer(bob, "Dev Java", "PUBLIEE", "Java");
        String alice = registerCandidate("alice@test.com");
        String carl = registerCandidate("carl@test.com");
        long applicationId = readId(postJson("/api/candidate/applications", alice,
                "{\"jobOfferId\":%d}".formatted(offer)), "$.id");

        getAs("/api/recruiter/jobs/" + offer + "/applications", eve).andExpect(status().isNotFound());
        getAs("/api/recruiter/applications/" + applicationId, eve).andExpect(status().isNotFound());
        patchJson("/api/recruiter/applications/" + applicationId + "/status", eve,
                "{\"status\":\"REFUSE\"}").andExpect(status().isNotFound());

        getAs("/api/candidate/applications/" + applicationId, carl).andExpect(status().isNotFound());
        getAs("/api/candidate/applications", carl).andExpect(jsonPath("$.length()").value(0));

        patchJson("/api/recruiter/applications/" + applicationId + "/status", alice,
                "{\"status\":\"ACCEPTE\"}").andExpect(status().isForbidden());
        postJson("/api/candidate/applications", bob, "{\"jobOfferId\":%d}".formatted(offer))
                .andExpect(status().isForbidden());
    }

    @Test
    void deletingOfferRemovesItsApplications() throws Exception {
        String bob = recruiterWithCompany("bob@acme.com", "Acme");
        long offer = createOffer(bob, "Dev Java", "PUBLIEE", "Java");
        String alice = registerCandidate("alice@test.com");
        postJson("/api/candidate/applications", alice, "{\"jobOfferId\":%d}".formatted(offer))
                .andExpect(status().isCreated());

        deleteAs("/api/recruiter/jobs/" + offer, bob).andExpect(status().isNoContent());

        getAs("/api/candidate/applications", alice).andExpect(jsonPath("$.length()").value(0));
    }
}
