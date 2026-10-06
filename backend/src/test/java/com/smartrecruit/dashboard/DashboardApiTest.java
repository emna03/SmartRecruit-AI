package com.smartrecruit.dashboard;

import com.smartrecruit.application.repository.ApplicationRepository;
import com.smartrecruit.candidate.entity.CandidateProfile;
import com.smartrecruit.candidate.repository.CandidateProfileRepository;
import com.smartrecruit.company.repository.CompanyRepository;
import com.smartrecruit.cv.entity.Cv;
import com.smartrecruit.cv.entity.CvParsingStatus;
import com.smartrecruit.cv.repository.CvRepository;
import com.smartrecruit.joboffer.entity.JobOfferStatus;
import com.smartrecruit.joboffer.repository.JobOfferRepository;
import com.smartrecruit.matching.entity.MatchingResult;
import com.smartrecruit.matching.repository.MatchingResultRepository;
import com.smartrecruit.skill.repository.SkillRepository;
import com.smartrecruit.support.ApiTestSupport;
import com.smartrecruit.user.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tableaux de bord candidat, recruteur et administrateur : chiffres comparés aux données réellement enregistrées
 * (H2 en test), périmètre de chaque rôle, isolation entre entreprises et droits d'accès.
 */
class DashboardApiTest extends ApiTestSupport {

    private static final String CANDIDATE = "/api/candidate/dashboard";
    private static final String RECRUITER = "/api/recruiter/dashboard";
    private static final String ADMIN = "/api/admin/dashboard";
    private static final String CURRENT_MONTH = YearMonth.now(ZoneOffset.UTC).toString();

    @Autowired
    private CandidateProfileRepository profileRepository;

    @Autowired
    private CvRepository cvRepository;

    @Autowired
    private MatchingResultRepository matchingResultRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private JobOfferRepository jobOfferRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private SkillRepository skillRepository;

    @Autowired
    private EntityManager entityManager;

    // ---------- Tableaux de bord vides ----------

    @Test
    void newUsersGetEmptyDashboardsWithZeros() throws Exception {
        String alice = registerCandidate("alice@test.com");
        getAs(CANDIDATE, alice).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalApplications").value(0))
                .andExpect(jsonPath("$.applicationsByStatus.EN_ATTENTE").value(0))
                .andExpect(jsonPath("$.applicationsByStatus.PRESELECTIONNE").value(0))
                .andExpect(jsonPath("$.applicationsByStatus.ACCEPTE").value(0))
                .andExpect(jsonPath("$.applicationsByStatus.REFUSE").value(0))
                .andExpect(jsonPath("$.publishedOffers").value(0))
                .andExpect(jsonPath("$.publishedOffersNotApplied").value(0))
                .andExpect(jsonPath("$.cv.state").value("ABSENT"))
                .andExpect(jsonPath("$.cv.parsingStatus").doesNotExist())
                .andExpect(jsonPath("$.scoredOffers").value(0))
                .andExpect(jsonPath("$.topMatches", hasSize(0)));

        String noCompany = registerRecruiter("solo@test.com");
        getAs(RECRUITER, noCompany).andExpect(status().isOk())
                .andExpect(jsonPath("$.hasCompany").value(false))
                .andExpect(jsonPath("$.totalOffers").value(0))
                .andExpect(jsonPath("$.offers", hasSize(0)));

        String bob = recruiterWithCompany("bob@test.com", "Acme");
        getAs(RECRUITER, bob).andExpect(status().isOk())
                .andExpect(jsonPath("$.hasCompany").value(true))
                .andExpect(jsonPath("$.companyName").value("Acme"))
                .andExpect(jsonPath("$.totalOffers").value(0))
                .andExpect(jsonPath("$.offersByStatus.PUBLIEE").value(0))
                .andExpect(jsonPath("$.totalApplications").value(0))
                .andExpect(jsonPath("$.scoredApplications").value(0))
                .andExpect(jsonPath("$.averageMatchingScore").doesNotExist())
                .andExpect(jsonPath("$.applicationsByMonth", hasSize(6)))
                .andExpect(jsonPath("$.applicationsByMonth[5].month").value(CURRENT_MONTH))
                .andExpect(jsonPath("$.applicationsByMonth[*].count").value(contains(0, 0, 0, 0, 0, 0)))
                .andExpect(jsonPath("$.topCandidates", hasSize(0)));
    }

    // ---------- Candidat ----------

    @Test
    void candidateSeesOwnApplicationsCvStateAndVisibleBestScores() throws Exception {
        String bob = recruiterWithCompany("bob@test.com", "Acme");
        long applied = createOffer(bob, "Dev Java", "PUBLIEE", "Java");
        long closedLater = createOffer(bob, "Dev Angular", "PUBLIEE", "Angular");
        long notApplied = createOffer(bob, "Dev Python", "PUBLIEE", "Python");
        long draft = createOffer(bob, "Data", "BROUILLON", "SQL");
        String alice = registerCandidate("alice@test.com");
        String other = registerCandidate("other@test.com");
        apply(alice, applied);
        apply(alice, closedLater);
        apply(other, applied);
        apply(other, notApplied);
        patchJson("/api/recruiter/jobs/" + closedLater + "/status", bob, "{\"status\":\"FERMEE\"}")
                .andExpect(status().isOk());
        patchJson("/api/recruiter/applications/" + applicationId("alice@test.com", applied) + "/status", bob,
                "{\"status\":\"PRESELECTIONNE\"}").andExpect(status().isOk());

        LocalDateTime parsedAt = LocalDateTime.now(ZoneOffset.UTC).minusHours(1);
        Cv cv = storeCv("alice@test.com", CvParsingStatus.TERMINE, parsedAt, "Java", "Angular");
        storeScore("alice@test.com", applied, "80.00", parsedAt.plusMinutes(5));
        storeScore("alice@test.com", closedLater, "70.00", parsedAt.plusMinutes(5));
        storeScore("alice@test.com", notApplied, "60.00", parsedAt.plusMinutes(5));
        storeScore("alice@test.com", draft, "95.00", parsedAt.plusMinutes(5));
        storeScore("other@test.com", applied, "99.00", parsedAt.plusMinutes(5));

        getAs(CANDIDATE, alice).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalApplications").value(2))
                .andExpect(jsonPath("$.applicationsByStatus.EN_ATTENTE").value(1))
                .andExpect(jsonPath("$.applicationsByStatus.PRESELECTIONNE").value(1))
                .andExpect(jsonPath("$.applicationsByStatus.ACCEPTE").value(0))
                .andExpect(jsonPath("$.publishedOffers").value(2))
                .andExpect(jsonPath("$.publishedOffersNotApplied").value(1))
                .andExpect(jsonPath("$.cv.state").value("ANALYSE"))
                .andExpect(jsonPath("$.cv.parsingStatus").value("TERMINE"))
                .andExpect(jsonPath("$.cv.extractedSkills").value(2))
                // Brouillon jamais visible ; offre fermée visible car le candidat y a postulé ; score d'autrui exclu.
                .andExpect(jsonPath("$.scoredOffers").value(3))
                .andExpect(jsonPath("$.topMatches[*].score").value(contains(80.0, 70.0, 60.0)))
                .andExpect(jsonPath("$.topMatches[*].jobOfferId").value(contains((int) applied, (int) closedLater,
                        (int) notApplied)))
                .andExpect(jsonPath("$.topMatches[*].applied").value(contains(true, true, false)))
                .andExpect(jsonPath("$.topMatches[1].offerStatus").value("FERMEE"))
                .andExpect(jsonPath("$.topMatches[0].companyName").value("Acme"))
                .andExpect(jsonPath("$.topMatches[0].title").value("Dev Java"))
                .andExpect(jsonPath("$.topMatches[0].stale").value(false));

        cv.setParsedAt(LocalDateTime.now(ZoneOffset.UTC));
        entityManager.flush();
        getAs(CANDIDATE, alice).andExpect(jsonPath("$.topMatches[0].stale").value(true));

        cv.setParsingStatus(CvParsingStatus.ECHEC);
        entityManager.flush();
        getAs(CANDIDATE, alice).andExpect(jsonPath("$.cv.state").value("IMPORTE"))
                .andExpect(jsonPath("$.cv.parsingStatus").value("ECHEC"));
    }

    @Test
    void statisticsFollowNewApplicationsAndStatusChanges() throws Exception {
        String bob = recruiterWithCompany("bob@test.com", "Acme");
        long offer = createOffer(bob, "Dev Java", "PUBLIEE", "Java");
        String alice = registerCandidate("alice@test.com");
        getAs(CANDIDATE, alice).andExpect(jsonPath("$.totalApplications").value(0))
                .andExpect(jsonPath("$.publishedOffersNotApplied").value(1));
        getAs(RECRUITER, bob).andExpect(jsonPath("$.totalApplications").value(0));

        apply(alice, offer);
        getAs(CANDIDATE, alice).andExpect(jsonPath("$.totalApplications").value(1))
                .andExpect(jsonPath("$.applicationsByStatus.EN_ATTENTE").value(1))
                .andExpect(jsonPath("$.publishedOffersNotApplied").value(0));
        getAs(RECRUITER, bob).andExpect(jsonPath("$.totalApplications").value(1))
                .andExpect(jsonPath("$.applicationsByStatus.EN_ATTENTE").value(1))
                .andExpect(jsonPath("$.offers[0].applications").value(1))
                .andExpect(jsonPath("$.applicationsByMonth[5].count").value(1));

        long application = applicationId("alice@test.com", offer);
        patchJson("/api/recruiter/applications/" + application + "/status", bob, "{\"status\":\"ACCEPTE\"}")
                .andExpect(status().isOk());
        getAs(CANDIDATE, alice).andExpect(jsonPath("$.applicationsByStatus.EN_ATTENTE").value(0))
                .andExpect(jsonPath("$.applicationsByStatus.ACCEPTE").value(1));
        getAs(RECRUITER, bob).andExpect(jsonPath("$.applicationsByStatus.EN_ATTENTE").value(0))
                .andExpect(jsonPath("$.applicationsByStatus.ACCEPTE").value(1))
                .andExpect(jsonPath("$.offers[0].applicationsByStatus.ACCEPTE").value(1));
        String admin = adminToken();
        getAs(ADMIN, admin).andExpect(jsonPath("$.applicationsByStatus.ACCEPTE").value(1))
                .andExpect(jsonPath("$.totalApplications").value(1));
    }

    // ---------- Recruteur ----------

    @Test
    void recruiterSeesOffersApplicationsAndScoresOfApplicantsOnly() throws Exception {
        String bob = recruiterWithCompany("bob@test.com", "Acme");
        long java = createOffer(bob, "Dev Java", "PUBLIEE", "Java");
        long python = createOffer(bob, "Dev Python", "PUBLIEE", "Python");
        long draft = createOffer(bob, "Data", "BROUILLON", "SQL");
        long closed = createOffer(bob, "Ancienne offre", "FERMEE", "PHP");
        String alice = registerCandidate("alice@test.com");
        String carl = registerCandidate("carl@test.com");
        String dora = registerCandidate("dora@test.com");
        rename("alice@test.com", "Alice", "Martin");
        rename("carl@test.com", "Carl", "Ben Ali");
        rename("dora@test.com", "Dora", "Trabelsi");
        apply(alice, java);
        apply(carl, java);
        apply(alice, python);
        patchJson("/api/recruiter/applications/" + applicationId("carl@test.com", java) + "/status", bob,
                "{\"status\":\"REFUSE\"}").andExpect(status().isOk());

        LocalDateTime parsedAt = LocalDateTime.now(ZoneOffset.UTC).minusHours(1);
        storeCv("alice@test.com", CvParsingStatus.TERMINE, parsedAt, "Java");
        storeCv("carl@test.com", CvParsingStatus.TERMINE, parsedAt.plusHours(2), "Java");
        storeScore("alice@test.com", java, "80.00", parsedAt.plusMinutes(1));
        storeScore("carl@test.com", java, "60.00", parsedAt.plusMinutes(1));
        storeScore("alice@test.com", python, "45.50", parsedAt.plusMinutes(1));
        // Dora a consulté l'offre sans postuler : son score n'entre dans aucune statistique du recruteur.
        storeScore("dora@test.com", java, "99.00", parsedAt.plusMinutes(1));

        getAs(RECRUITER, bob).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOffers").value(4))
                .andExpect(jsonPath("$.offersByStatus.PUBLIEE").value(2))
                .andExpect(jsonPath("$.offersByStatus.BROUILLON").value(1))
                .andExpect(jsonPath("$.offersByStatus.FERMEE").value(1))
                .andExpect(jsonPath("$.totalApplications").value(3))
                .andExpect(jsonPath("$.applicationsByStatus.EN_ATTENTE").value(2))
                .andExpect(jsonPath("$.applicationsByStatus.REFUSE").value(1))
                .andExpect(jsonPath("$.scoredApplications").value(3))
                .andExpect(jsonPath("$.averageMatchingScore").value(61.8))
                .andExpect(jsonPath("$.applicationsByMonth[5].count").value(3))
                .andExpect(jsonPath("$.offers[*].id").value(contains((int) closed, (int) draft, (int) python,
                        (int) java)))
                .andExpect(jsonPath("$.offers[3].title").value("Dev Java"))
                .andExpect(jsonPath("$.offers[3].applications").value(2))
                .andExpect(jsonPath("$.offers[3].applicationsByStatus.REFUSE").value(1))
                .andExpect(jsonPath("$.offers[3].scoredApplications").value(2))
                .andExpect(jsonPath("$.offers[3].averageScore").value(70.0))
                .andExpect(jsonPath("$.offers[3].bestScore").value(80.0))
                .andExpect(jsonPath("$.offers[2].averageScore").value(45.5))
                .andExpect(jsonPath("$.offers[1].applications").value(0))
                .andExpect(jsonPath("$.offers[1].averageScore").doesNotExist())
                .andExpect(jsonPath("$.topCandidates[*].candidateName")
                        .value(contains("Alice Martin", "Carl Ben Ali", "Alice Martin")))
                .andExpect(jsonPath("$.topCandidates[*].score").value(contains(80.0, 60.0, 45.5)))
                .andExpect(jsonPath("$.topCandidates[0].jobTitle").value("Dev Java"))
                .andExpect(jsonPath("$.topCandidates[0].applicationId")
                        .value((int) applicationId("alice@test.com", java)))
                .andExpect(jsonPath("$.topCandidates[0].status").value("EN_ATTENTE"))
                .andExpect(jsonPath("$.topCandidates[0].stale").value(false))
                .andExpect(jsonPath("$.topCandidates[1].status").value("REFUSE"))
                // CV de Carl réanalysé après le calcul : score signalé comme à recalculer.
                .andExpect(jsonPath("$.topCandidates[1].stale").value(true));
    }

    @Test
    void recruitersOnlySeeTheirOwnCompany() throws Exception {
        String bob = recruiterWithCompany("bob@test.com", "Acme");
        String eve = recruiterWithCompany("eve@test.com", "Globex");
        long acmeOffer = createOffer(bob, "Dev Java", "PUBLIEE", "Java");
        createOffer(bob, "Dev Python", "BROUILLON", "Python");
        long globexOffer = createOffer(eve, "Comptable", "PUBLIEE", "Excel");
        String alice = registerCandidate("alice@test.com");
        apply(alice, acmeOffer);
        apply(alice, globexOffer);
        storeCv("alice@test.com", CvParsingStatus.TERMINE, LocalDateTime.now(ZoneOffset.UTC).minusHours(1), "Java");
        storeScore("alice@test.com", acmeOffer, "90.00", LocalDateTime.now(ZoneOffset.UTC));
        storeScore("alice@test.com", globexOffer, "20.00", LocalDateTime.now(ZoneOffset.UTC));

        getAs(RECRUITER, eve).andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("Globex"))
                .andExpect(jsonPath("$.totalOffers").value(1))
                .andExpect(jsonPath("$.offers[*].title").value(contains("Comptable")))
                .andExpect(jsonPath("$.totalApplications").value(1))
                .andExpect(jsonPath("$.averageMatchingScore").value(20.0))
                .andExpect(jsonPath("$.topCandidates[*].jobTitle").value(contains("Comptable")));
        getAs(RECRUITER, bob)
                .andExpect(jsonPath("$.totalOffers").value(2))
                .andExpect(jsonPath("$.totalApplications").value(1))
                .andExpect(jsonPath("$.averageMatchingScore").value(90.0))
                .andExpect(jsonPath("$.topCandidates[*].jobTitle").value(contains("Dev Java")));

        // Un second recruteur de la même entreprise voit les mêmes statistiques d'entreprise.
        String bobColleague = registerRecruiter("colleague@test.com");
        var colleague = profileOfRecruiter("colleague@test.com");
        colleague.setCompany(companyRepository.findAll().stream()
                .filter(c -> c.getName().equals("Acme")).findFirst().orElseThrow());
        entityManager.flush();
        getAs(RECRUITER, bobColleague).andExpect(jsonPath("$.totalOffers").value(2))
                .andExpect(jsonPath("$.totalApplications").value(1));
    }

    // ---------- Administrateur ----------

    @Test
    void adminSeesGlobalStatisticsMatchingTheDatabase() throws Exception {
        String bob = recruiterWithCompany("bob@test.com", "Acme");
        String eve = recruiterWithCompany("eve@test.com", "Globex");
        registerRecruiter("solo@test.com");
        long java = createOffer(bob, "Dev Java", "PUBLIEE", "Java", "Spring Boot");
        long python = createOffer(eve, "Dev Python", "PUBLIEE", "Python", "Java");
        createOffer(eve, "Data", "BROUILLON", "SQL", "Java");
        createOffer(bob, "Ancienne", "FERMEE", "PHP");
        String alice = registerCandidate("alice@test.com");
        String carl = registerCandidate("carl@test.com");
        registerCandidate("dora@test.com");
        apply(alice, java);
        apply(carl, java);
        apply(alice, python);
        patchJson("/api/recruiter/applications/" + applicationId("carl@test.com", java) + "/status", bob,
                "{\"status\":\"PRESELECTIONNE\"}").andExpect(status().isOk());
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        storeCv("alice@test.com", CvParsingStatus.TERMINE, now, "Java");
        storeCv("carl@test.com", CvParsingStatus.ECHEC, null);
        storeScore("alice@test.com", java, "80.00", now);
        storeScore("alice@test.com", python, "50.00", now);
        String admin = adminToken();
        long dora = userRepository.findByEmailIgnoreCase("dora@test.com").orElseThrow().getId();
        patchJson("/api/admin/users/" + dora + "/status", admin, "{\"enabled\":false}").andExpect(status().isOk());

        ResultActions result = getAs(ADMIN, admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").value(7))
                .andExpect(jsonPath("$.usersByRole.ADMIN").value(1))
                .andExpect(jsonPath("$.usersByRole.RECRUTEUR").value(3))
                .andExpect(jsonPath("$.usersByRole.CANDIDAT").value(3))
                .andExpect(jsonPath("$.disabledUsers").value(1))
                .andExpect(jsonPath("$.companies").value(2))
                .andExpect(jsonPath("$.recruitersWithoutCompany").value(1))
                .andExpect(jsonPath("$.totalOffers").value(4))
                .andExpect(jsonPath("$.offersByStatus.PUBLIEE").value(2))
                .andExpect(jsonPath("$.offersByStatus.BROUILLON").value(1))
                .andExpect(jsonPath("$.offersByStatus.FERMEE").value(1))
                .andExpect(jsonPath("$.publishedOffersByContract.CDI").value(2))
                .andExpect(jsonPath("$.publishedOffersByContract.STAGE").value(0))
                .andExpect(jsonPath("$.totalApplications").value(3))
                .andExpect(jsonPath("$.applicationsByStatus.EN_ATTENTE").value(2))
                .andExpect(jsonPath("$.applicationsByStatus.PRESELECTIONNE").value(1))
                .andExpect(jsonPath("$.candidatesWithCv").value(2))
                .andExpect(jsonPath("$.cvsByParsingStatus.TERMINE").value(1))
                .andExpect(jsonPath("$.cvsByParsingStatus.ECHEC").value(1))
                .andExpect(jsonPath("$.storedMatchingScores").value(2))
                .andExpect(jsonPath("$.averageMatchingScore").value(65.0))
                // Java demandé par les 2 offres publiées (le brouillon ne compte pas), puis ordre alphabétique.
                .andExpect(jsonPath("$.topRequestedSkills[*].skill").value(contains("Java", "Python", "Spring Boot")))
                .andExpect(jsonPath("$.topRequestedSkills[*].offers").value(contains(2, 1, 1)))
                .andExpect(jsonPath("$.activityByMonth", hasSize(6)))
                .andExpect(jsonPath("$.activityByMonth[5].month").value(CURRENT_MONTH))
                .andExpect(jsonPath("$.activityByMonth[5].newUsers").value(7))
                .andExpect(jsonPath("$.activityByMonth[5].newOffers").value(4))
                .andExpect(jsonPath("$.activityByMonth[5].applications").value(3))
                .andExpect(jsonPath("$.activityByMonth[0].newUsers").value(0));

        assertThat(((Number) read(result, "$.totalUsers")).longValue()).isEqualTo(userRepository.count());
        assertThat(((Number) read(result, "$.totalOffers")).longValue()).isEqualTo(jobOfferRepository.count());
        assertThat(((Number) read(result, "$.totalApplications")).longValue())
                .isEqualTo(applicationRepository.count());
        assertThat(((Number) read(result, "$.companies")).longValue()).isEqualTo(companyRepository.count());
        assertThat(((Number) read(result, "$.storedMatchingScores")).longValue())
                .isEqualTo(matchingResultRepository.count());
        assertThat(((Number) read(result, "$.offersByStatus.PUBLIEE")).longValue())
                .isEqualTo(jobOfferRepository.findByStatus(JobOfferStatus.PUBLIEE).size());
    }

    // ---------- Accès ----------

    @Test
    void eachDashboardIsReservedToItsRole() throws Exception {
        String alice = registerCandidate("alice@test.com");
        String bob = recruiterWithCompany("bob@test.com", "Acme");
        String admin = adminToken();

        for (String path : new String[]{CANDIDATE, RECRUITER, ADMIN}) {
            getAs(path, null).andExpect(status().isUnauthorized());
            getAs(path, "jeton-invalide").andExpect(status().isUnauthorized());
        }
        getAs(RECRUITER, alice).andExpect(status().isForbidden());
        getAs(ADMIN, alice).andExpect(status().isForbidden());
        getAs(CANDIDATE, bob).andExpect(status().isForbidden());
        getAs(ADMIN, bob).andExpect(status().isForbidden());
        getAs(CANDIDATE, admin).andExpect(status().isForbidden());
        getAs(RECRUITER, admin).andExpect(status().isForbidden());

        getAs(CANDIDATE, alice).andExpect(status().isOk());
        getAs(RECRUITER, bob).andExpect(status().isOk());
        getAs(ADMIN, admin).andExpect(status().isOk());
    }

    @Test
    void dashboardsHaveNoIdentifierToTargetAnotherUser() throws Exception {
        String bob = recruiterWithCompany("bob@test.com", "Acme");
        String eve = recruiterWithCompany("eve@test.com", "Globex");
        createOffer(bob, "Dev Java", "PUBLIEE", "Java");
        long globexId = companyRepository.findAll().stream().filter(c -> c.getName().equals("Globex"))
                .findFirst().orElseThrow().getId();

        // Un paramètre ajouté par le client ne change pas le périmètre : il vient uniquement du jeton.
        getAs(RECRUITER + "?companyId=" + globexId, bob).andExpect(jsonPath("$.companyName").value("Acme"))
                .andExpect(jsonPath("$.totalOffers").value(1));
        getAs(RECRUITER + "/" + globexId, bob).andExpect(status().isNotFound());
        getAs(RECRUITER, eve).andExpect(jsonPath("$.totalOffers").value(0));
    }

    // ---------- Utilitaires ----------

    private void apply(String token, long offer) throws Exception {
        postJson("/api/candidate/applications", token, "{\"jobOfferId\":" + offer + "}")
                .andExpect(status().isCreated());
    }

    private long applicationId(String email, long offer) {
        long candidateId = candidate(email).getId();
        return applicationRepository.findByCandidateIdOrderByCreatedAtDesc(candidateId).stream()
                .filter(a -> a.getJobOffer().getId() == offer).findFirst().orElseThrow().getId();
    }

    private CandidateProfile candidate(String email) {
        long userId = userRepository.findByEmailIgnoreCase(email).orElseThrow().getId();
        return profileRepository.findByUserId(userId).orElseThrow();
    }

    private com.smartrecruit.recruiter.entity.RecruiterProfile profileOfRecruiter(String email) {
        long userId = userRepository.findByEmailIgnoreCase(email).orElseThrow().getId();
        return entityManager.createQuery(
                        "select r from RecruiterProfile r where r.user.id = :userId",
                        com.smartrecruit.recruiter.entity.RecruiterProfile.class)
                .setParameter("userId", userId).getSingleResult();
    }

    private void rename(String email, String firstName, String lastName) {
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        user.setFirstName(firstName);
        user.setLastName(lastName);
        entityManager.flush();
    }

    private Cv storeCv(String email, CvParsingStatus status, LocalDateTime parsedAt, String... skills) {
        Cv cv = new Cv();
        cv.setCandidate(candidate(email));
        cv.setOriginalFileName("cv.pdf");
        cv.setStoredFileName(UUID.randomUUID() + ".pdf");
        cv.setContentType("application/pdf");
        cv.setFileSize(1024);
        cv.setParsingStatus(status);
        cv.setParsedAt(parsedAt);
        for (String skill : skills) {
            cv.getExtractedSkills().add(skillRepository.findByNameIgnoreCase(skill).orElseGet(
                    () -> skillRepository.save(new com.smartrecruit.skill.entity.Skill(skill, null))));
        }
        cvRepository.saveAndFlush(cv);
        return cv;
    }

    private void storeScore(String email, long offer, String score, LocalDateTime computedAt) {
        MatchingResult result = new MatchingResult();
        result.setCandidate(candidate(email));
        result.setJobOffer(jobOfferRepository.getReferenceById(offer));
        result.setScore(new BigDecimal(score));
        result.setAlgorithmVersion("1.0.0");
        result.setComputedAt(computedAt);
        matchingResultRepository.saveAndFlush(result);
    }
}
