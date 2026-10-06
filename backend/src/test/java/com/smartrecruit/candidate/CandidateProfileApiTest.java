package com.smartrecruit.candidate;

import com.smartrecruit.skill.repository.SkillRepository;
import com.smartrecruit.support.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CandidateProfileApiTest extends ApiTestSupport {

    @Autowired
    private SkillRepository skillRepository;

    @Test
    void candidateReadsAndUpdatesProfile() throws Exception {
        String token = registerCandidate("alice@test.com");

        getAs("/api/candidate/profile", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice@test.com"))
                .andExpect(jsonPath("$.skills", hasSize(0)))
                .andExpect(jsonPath("$.hasCv").value(false));

        putJson("/api/candidate/profile", token, """
                {"firstName":"Alice","lastName":"Martin","headline":"Développeuse Java","summary":"Passionnée",
                 "phone":"+216 20 123 456","location":"Tunis","yearsOfExperience":3,"educationLevel":"BAC_PLUS_5"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Alice"))
                .andExpect(jsonPath("$.headline").value("Développeuse Java"))
                .andExpect(jsonPath("$.yearsOfExperience").value(3))
                .andExpect(jsonPath("$.educationLevel").value("BAC_PLUS_5"));
    }

    @Test
    void invalidProfileDataIsRejected() throws Exception {
        String token = registerCandidate("bad@test.com");

        putJson("/api/candidate/profile", token, """
                {"firstName":"","lastName":"X","phone":"abc","yearsOfExperience":-1}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.firstName").exists())
                .andExpect(jsonPath("$.fieldErrors.phone").exists())
                .andExpect(jsonPath("$.fieldErrors.yearsOfExperience").exists());

        putJson("/api/candidate/profile", token, """
                {"firstName":"A","lastName":"B","educationLevel":"MASTER_INCONNU"}
                """)
                .andExpect(status().isBadRequest());
    }

    @Test
    void skillsAreReplacedDeduplicatedAndSharedInCatalog() throws Exception {
        String alice = registerCandidate("alice@test.com");
        String bob = registerCandidate("bob@test.com");

        putJson("/api/candidate/profile/skills", alice, """
                {"skills":["Java","  spring   boot ","java","Angular"]}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skills", contains("Angular", "Java", "spring boot")));

        putJson("/api/candidate/profile/skills", bob, """
                {"skills":["JAVA","Docker"]}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skills", contains("Docker", "Java")));

        assertThat(skillRepository.findAll()).hasSize(4);

        putJson("/api/candidate/profile/skills", alice, """
                {"skills":["Python"]}
                """)
                .andExpect(jsonPath("$.skills", contains("Python")));
    }

    @Test
    void educationsCanBeAddedUpdatedAndDeleted() throws Exception {
        String token = registerCandidate("carl@test.com");

        var created = postJson("/api/candidate/profile/educations", token, """
                {"degree":"Ingénieur logiciel","institution":"ESPRIT","startDate":"2021-09-01","endDate":"2026-06-30"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.educations", hasSize(1)))
                .andExpect(jsonPath("$.educations[0].id").isNumber());
        long id = readId(created, "$.educations[0].id");

        putJson("/api/candidate/profile/educations/" + id, token, """
                {"degree":"Ingénieur logiciel","fieldOfStudy":"Génie logiciel","institution":"ESPRIT"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.educations[0].fieldOfStudy").value("Génie logiciel"));

        deleteAs("/api/candidate/profile/educations/" + id, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.educations", hasSize(0)));
    }

    @Test
    void experienceValidatesDates() throws Exception {
        String token = registerCandidate("dina@test.com");

        postJson("/api/candidate/profile/experiences", token, """
                {"jobTitle":"Stagiaire","companyName":"Acme","startDate":"2025-06-01","endDate":"2025-01-01"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La date de fin doit être postérieure à la date de début"));

        postJson("/api/candidate/profile/experiences", token, """
                {"jobTitle":"Stagiaire","companyName":"Acme","startDate":"2099-01-01"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.startDate").exists());

        postJson("/api/candidate/profile/experiences", token, """
                {"jobTitle":"Développeur","companyName":"Acme","startDate":"2025-01-01"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.experiences[0].current").value(true));
    }

    @Test
    void candidateCannotModifyAnotherCandidatesEntries() throws Exception {
        String owner = registerCandidate("owner@test.com");
        String other = registerCandidate("other@test.com");
        long id = readId(postJson("/api/candidate/profile/experiences", owner, """
                {"jobTitle":"Dev","companyName":"Acme","startDate":"2024-01-01"}
                """), "$.experiences[0].id");

        putJson("/api/candidate/profile/experiences/" + id, other, """
                {"jobTitle":"Pirate","companyName":"X","startDate":"2024-01-01"}
                """).andExpect(status().isNotFound());
        deleteAs("/api/candidate/profile/experiences/" + id, other).andExpect(status().isNotFound());

        getAs("/api/candidate/profile", owner).andExpect(jsonPath("$.experiences[0].jobTitle").value("Dev"));
    }

    @Test
    void recruiterCannotAccessCandidateProfileEndpoints() throws Exception {
        String recruiter = registerRecruiter("recr@test.com");

        getAs("/api/candidate/profile", recruiter).andExpect(status().isForbidden());
        getAs("/api/candidate/profile", null).andExpect(status().isUnauthorized());
    }
}
