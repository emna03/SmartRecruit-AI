package com.smartrecruit.company;

import com.smartrecruit.support.ApiTestSupport;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RecruiterCompanyApiTest extends ApiTestSupport {

    @Test
    void recruiterReadsAndUpdatesProfile() throws Exception {
        String token = registerRecruiter("bob@acme.com");

        getAs("/api/recruiter/profile", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("bob@acme.com"))
                .andExpect(jsonPath("$.company").doesNotExist());

        putJson("/api/recruiter/profile", token, """
                {"firstName":"Bob","lastName":"Durand","jobTitle":"Responsable RH","phone":"+216 71 000 000"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobTitle").value("Responsable RH"));
    }

    @Test
    void recruiterCreatesThenUpdatesHisCompany() throws Exception {
        String token = registerRecruiter("bob@acme.com");

        getAs("/api/recruiter/company", token).andExpect(status().isNotFound());

        postJson("/api/recruiter/company", token, """
                {"name":"Acme","sector":"IT","website":"https://acme.example","location":"Tunis"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Acme"))
                .andExpect(jsonPath("$.recruiterCount").value(1));

        getAs("/api/recruiter/profile", token).andExpect(jsonPath("$.company.name").value("Acme"));

        putJson("/api/recruiter/company", token, """
                {"name":"Acme Corp","sector":"Logiciel","location":"Sfax"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Acme Corp"))
                .andExpect(jsonPath("$.location").value("Sfax"));
    }

    @Test
    void companyRulesAreEnforced() throws Exception {
        String bob = recruiterWithCompany("bob@acme.com", "Acme");
        String eve = registerRecruiter("eve@globex.com");

        postJson("/api/recruiter/company", bob, """
                {"name":"Second"}
                """).andExpect(status().isConflict());
        postJson("/api/recruiter/company", eve, """
                {"name":"ACME"}
                """).andExpect(status().isConflict());
        postJson("/api/recruiter/company", eve, """
                {"name":"","website":"ftp://x"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.website").exists());
        putJson("/api/recruiter/company", eve, """
                {"name":"Globex"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Vous devez d'abord créer votre entreprise"));
    }

    @Test
    void candidateCannotManageCompanies() throws Exception {
        String candidate = registerCandidate("alice@test.com");

        postJson("/api/recruiter/company", candidate, """
                {"name":"Fake"}
                """).andExpect(status().isForbidden());
        getAs("/api/admin/companies", candidate).andExpect(status().isForbidden());
    }

    @Test
    void adminListsUpdatesAndDeletesCompanies() throws Exception {
        String bob = recruiterWithCompany("bob@acme.com", "Acme");
        createOffer(bob, "Dev Java", "PUBLIEE", "Java");
        recruiterWithCompany("eve@globex.com", "Globex");
        String admin = adminToken();

        var list = getAs("/api/admin/companies", admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Acme"))
                .andExpect(jsonPath("$[0].jobOfferCount").value(1));
        long acmeId = readId(list, "$[0].id");

        putJson("/api/admin/companies/" + acmeId, admin, """
                {"name":"Acme Renamed"}
                """).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Acme Renamed"));

        deleteAs("/api/admin/companies/" + acmeId, admin).andExpect(status().isNoContent());
        getAs("/api/admin/companies/" + acmeId, admin).andExpect(status().isNotFound());
        getAs("/api/jobs", null).andExpect(jsonPath("$.totalElements").value(0));
        getAs("/api/recruiter/profile", bob).andExpect(jsonPath("$.company").doesNotExist());
    }
}
