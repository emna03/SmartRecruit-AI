package com.smartrecruit.dashboard;

import com.smartrecruit.support.ApiTestSupport;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Pas de requête N+1 : le nombre de requêtes SQL d'un tableau de bord ne dépend pas du volume de données. */
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class DashboardQueryCountTest extends ApiTestSupport {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void queryCountDoesNotGrowWithOffersApplicationsAndCandidates() throws Exception {
        String bob = recruiterWithCompany("bob@test.com", "Acme");
        String alice = registerCandidate("alice@test.com");
        String admin = adminToken();
        apply(alice, createOffer(bob, "Offre 0", "PUBLIEE", "Java"));
        long[] small = {queries("/api/recruiter/dashboard", bob), queries("/api/candidate/dashboard", alice),
                queries("/api/admin/dashboard", admin)};

        for (int i = 1; i <= 5; i++) {
            long offer = createOffer(bob, "Offre " + i, "PUBLIEE", "Skill " + i, "Java");
            apply(alice, offer);
            apply(registerCandidate("candidat" + i + "@test.com"), offer);
        }
        long[] large = {queries("/api/recruiter/dashboard", bob), queries("/api/candidate/dashboard", alice),
                queries("/api/admin/dashboard", admin)};

        assertThat(Arrays.stream(small).min().orElseThrow()).isPositive();
        assertThat(large).containsExactly(small);
        assertThat(small[0]).isLessThanOrEqualTo(10);
        assertThat(small[2]).isLessThanOrEqualTo(20);
    }

    private long queries(String path, String token) throws Exception {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        getAs(path, token).andExpect(status().isOk());
        return statistics.getPrepareStatementCount();
    }

    private void apply(String token, long offer) throws Exception {
        postJson("/api/candidate/applications", token, "{\"jobOfferId\":" + offer + "}")
                .andExpect(status().isCreated());
    }
}
