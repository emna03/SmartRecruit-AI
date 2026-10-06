package com.smartrecruit.joboffer;

import com.smartrecruit.support.ApiTestSupport;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class JobOfferApiTest extends ApiTestSupport {

    private static final String VALID_OFFER = """
            {"title":"Développeur Full Stack","description":"Java / Angular","location":"Tunis","contractType":"CDI",
             "minExperienceYears":2,"educationLevel":"BAC_PLUS_5","salaryMin":2000,"salaryMax":3500,
             "requiredSkills":["Java","Spring Boot","Angular","MySQL","Docker"]}
            """;

    @Test
    void recruiterWithoutCompanyCannotCreateOffer() throws Exception {
        String token = registerRecruiter("bob@acme.com");

        postJson("/api/recruiter/jobs", token, VALID_OFFER)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Vous devez d'abord créer votre entreprise"));
    }

    @Test
    void recruiterCreatesDraftOfferThenPublishesIt() throws Exception {
        String token = recruiterWithCompany("bob@acme.com", "Acme");

        var created = postJson("/api/recruiter/jobs", token, VALID_OFFER)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("BROUILLON"))
                .andExpect(jsonPath("$.company.name").value("Acme"))
                .andExpect(jsonPath("$.applicationCount").value(0))
                .andExpect(jsonPath("$.requiredSkills", containsInAnyOrder("Java", "Spring Boot", "Angular", "MySQL", "Docker")));
        long id = readId(created, "$.id");

        getAs("/api/jobs/" + id, null).andExpect(status().isNotFound());

        patchJson("/api/recruiter/jobs/" + id + "/status", token, """
                {"status":"PUBLIEE"}
                """).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLIEE"));

        getAs("/api/jobs/" + id, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Développeur Full Stack"))
                .andExpect(jsonPath("$.applicationCount").doesNotExist());
    }

    @Test
    void offerValidationRules() throws Exception {
        String token = recruiterWithCompany("bob@acme.com", "Acme");

        postJson("/api/recruiter/jobs", token, """
                {"title":"","description":"","contractType":"CDI","minExperienceYears":-1,"requiredSkills":[]}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").exists())
                .andExpect(jsonPath("$.fieldErrors.description").exists())
                .andExpect(jsonPath("$.fieldErrors.minExperienceYears").exists())
                .andExpect(jsonPath("$.fieldErrors.requiredSkills").exists());

        postJson("/api/recruiter/jobs", token, """
                {"title":"T","description":"D","contractType":"CDI","minExperienceYears":0,
                 "salaryMin":3000,"salaryMax":1000,"requiredSkills":["Java"]}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Le salaire maximum doit être supérieur ou égal au salaire minimum"));

        postJson("/api/recruiter/jobs", token, """
                {"title":"T","description":"D","contractType":"INTERIM","minExperienceYears":0,"requiredSkills":["Java"]}
                """).andExpect(status().isBadRequest());
    }

    @Test
    void recruiterManagesOnlyHisCompanyOffers() throws Exception {
        String bob = recruiterWithCompany("bob@acme.com", "Acme");
        String eve = recruiterWithCompany("eve@globex.com", "Globex");
        long bobOffer = createOffer(bob, "Dev Java", "PUBLIEE", "Java");
        createOffer(eve, "Data Analyst", "PUBLIEE", "Python");

        getAs("/api/recruiter/jobs", bob)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Dev Java"));

        getAs("/api/recruiter/jobs/" + bobOffer, eve).andExpect(status().isNotFound());
        putJson("/api/recruiter/jobs/" + bobOffer, eve, VALID_OFFER).andExpect(status().isNotFound());
        deleteAs("/api/recruiter/jobs/" + bobOffer, eve).andExpect(status().isNotFound());

        putJson("/api/recruiter/jobs/" + bobOffer, bob, VALID_OFFER)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Développeur Full Stack"))
                .andExpect(jsonPath("$.status").value("PUBLIEE"));

        deleteAs("/api/recruiter/jobs/" + bobOffer, bob).andExpect(status().isNoContent());
        getAs("/api/recruiter/jobs/" + bobOffer, bob).andExpect(status().isNotFound());
    }

    @Test
    void publicSearchShowsOnlyPublishedOffersAndAppliesFilters() throws Exception {
        String bob = recruiterWithCompany("bob@acme.com", "Acme");
        createOffer(bob, "Développeur Java", "PUBLIEE", "Java", "Spring Boot");
        createOffer(bob, "Développeur Angular", "PUBLIEE", "Angular", "TypeScript");
        createOffer(bob, "Data Scientist", "PUBLIEE", "Python");
        createOffer(bob, "Java brouillon", "BROUILLON", "Java");
        createOffer(bob, "Java fermée", "FERMEE", "Java");

        getAs("/api/jobs", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));
        getAs("/api/jobs?keyword=java", null)
                .andExpect(jsonPath("$.content[*].title", contains("Développeur Java")));
        getAs("/api/jobs?skill=PYTHON", null)
                .andExpect(jsonPath("$.content[*].title", contains("Data Scientist")));
        getAs("/api/jobs?keyword=développeur&size=1&page=1", null)
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(1));
        getAs("/api/jobs?contractType=STAGE", null)
                .andExpect(jsonPath("$.totalElements").value(0));
        getAs("/api/jobs?location=tun&maxExperienceYears=2", null)
                .andExpect(jsonPath("$.totalElements").value(3));
        getAs("/api/jobs?maxExperienceYears=1", null)
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void candidateCannotUseRecruiterEndpoints() throws Exception {
        String candidate = registerCandidate("alice@test.com");

        postJson("/api/recruiter/jobs", candidate, VALID_OFFER).andExpect(status().isForbidden());
        getAs("/api/recruiter/jobs", candidate).andExpect(status().isForbidden());
    }

    @Test
    void adminListsAllOffersAndCanCloseOrDeleteThem() throws Exception {
        String bob = recruiterWithCompany("bob@acme.com", "Acme");
        long published = createOffer(bob, "Dev Java", "PUBLIEE", "Java");
        createOffer(bob, "Brouillon", "BROUILLON", "Java");
        String admin = adminToken();

        getAs("/api/admin/jobs", admin).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        getAs("/api/admin/jobs?status=BROUILLON", admin).andExpect(jsonPath("$.length()").value(1));

        patchJson("/api/admin/jobs/" + published + "/status", admin, """
                {"status":"FERMEE"}
                """).andExpect(status().isOk());
        getAs("/api/jobs/" + published, null).andExpect(status().isNotFound());

        deleteAs("/api/admin/jobs/" + published, admin).andExpect(status().isNoContent());
        getAs("/api/admin/jobs", admin).andExpect(jsonPath("$.length()").value(1));
    }
}
