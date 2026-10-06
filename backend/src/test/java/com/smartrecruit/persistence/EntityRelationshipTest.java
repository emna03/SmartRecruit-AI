package com.smartrecruit.persistence;

import com.smartrecruit.application.entity.Application;
import com.smartrecruit.application.entity.ApplicationStatus;
import com.smartrecruit.application.repository.ApplicationRepository;
import com.smartrecruit.candidate.entity.CandidateProfile;
import com.smartrecruit.candidate.entity.Education;
import com.smartrecruit.candidate.entity.Experience;
import com.smartrecruit.candidate.repository.CandidateProfileRepository;
import com.smartrecruit.common.model.EducationLevel;
import com.smartrecruit.company.entity.Company;
import com.smartrecruit.company.repository.CompanyRepository;
import com.smartrecruit.cv.entity.Cv;
import com.smartrecruit.cv.entity.CvParsingStatus;
import com.smartrecruit.joboffer.entity.ContractType;
import com.smartrecruit.joboffer.entity.JobOffer;
import com.smartrecruit.joboffer.entity.JobOfferStatus;
import com.smartrecruit.matching.entity.MatchingResult;
import com.smartrecruit.matching.repository.MatchingResultRepository;
import com.smartrecruit.recruiter.entity.RecruiterProfile;
import com.smartrecruit.skill.entity.Skill;
import com.smartrecruit.user.entity.Role;
import com.smartrecruit.user.entity.RoleName;
import com.smartrecruit.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
// La base embarquée de remplacement de Spring fait échouer les contraintes CHECK (enums) sous H2 2.4 :
// on garde la base H2 définie dans application-test.yml.
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class EntityRelationshipTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private CandidateProfileRepository candidateRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private MatchingResultRepository matchingRepository;

    private Role candidateRole;
    private Role recruiterRole;
    private Skill java;
    private Skill angular;
    private Skill docker;

    @BeforeEach
    void setUp() {
        candidateRole = em.persist(new Role(RoleName.CANDIDAT));
        recruiterRole = em.persist(new Role(RoleName.RECRUTEUR));
        java = em.persist(new Skill("Java", "Langage"));
        angular = em.persist(new Skill("Angular", "Framework"));
        docker = em.persist(new Skill("Docker", "DevOps"));
    }

    @Test
    void persistsFullGraphAndReloadsAllRelations() {
        CandidateProfile candidate = newCandidate("alice@test.com");
        candidate.getSkills().add(java);
        candidate.getSkills().add(angular);

        Education education = new Education();
        education.setDegree("DiplÃ´me d'ingÃ©nieur");
        education.setInstitution("ESPRIT");
        education.setStartDate(LocalDate.of(2021, 9, 1));
        candidate.addEducation(education);

        Experience experience = new Experience();
        experience.setJobTitle("Stagiaire dÃ©veloppeur");
        experience.setCompanyName("TechCorp");
        experience.setStartDate(LocalDate.of(2025, 6, 1));
        candidate.addExperience(experience);

        Cv cv = new Cv();
        cv.setOriginalFileName("cv.pdf");
        cv.setStoredFileName("3f2a-cv.pdf");
        cv.setContentType("application/pdf");
        cv.setFileSize(1024);
        cv.setParsingStatus(CvParsingStatus.TERMINE);
        cv.getExtractedSkills().add(java);
        candidate.attachCv(cv);
        em.persist(candidate);

        Company company = newCompany("Acme");
        RecruiterProfile recruiter = newRecruiter("bob@acme.com", company);
        JobOffer offer = newOffer(company, recruiter, "DÃ©veloppeur Full Stack");
        offer.getRequiredSkills().add(java);
        offer.getRequiredSkills().add(docker);

        Application application = new Application();
        application.setCandidate(candidate);
        application.setJobOffer(offer);
        em.persist(application);
        em.flush();
        em.clear();

        CandidateProfile reloaded = candidateRepository.findById(candidate.getId()).orElseThrow();
        assertThat(reloaded.getUser().getRole().getName()).isEqualTo(RoleName.CANDIDAT);
        assertThat(reloaded.getSkills()).extracting(Skill::getName).containsExactlyInAnyOrder("Java", "Angular");
        assertThat(reloaded.getEducations()).hasSize(1);
        assertThat(reloaded.getExperiences()).hasSize(1);
        assertThat(reloaded.getCv().getExtractedSkills()).extracting(Skill::getName).containsExactly("Java");
        assertThat(reloaded.getApplications()).hasSize(1);
        assertThat(reloaded.getCreatedAt()).isNotNull();

        Company reloadedCompany = companyRepository.findById(company.getId()).orElseThrow();
        JobOffer reloadedOffer = reloadedCompany.getJobOffers().getFirst();
        assertThat(reloadedOffer.getRecruiter().getUser().getEmail()).isEqualTo("bob@acme.com");
        assertThat(reloadedOffer.getRequiredSkills()).extracting(Skill::getName).containsExactlyInAnyOrder("Java", "Docker");
        assertThat(reloadedOffer.getApplications().getFirst().getStatus()).isEqualTo(ApplicationStatus.EN_ATTENTE);
    }

    @Test
    void rejectsSecondApplicationToSameOffer() {
        CandidateProfile candidate = em.persist(newCandidate("carl@test.com"));
        Company company = newCompany("Globex");
        JobOffer offer = newOffer(company, null, "Data Analyst");

        Application first = new Application();
        first.setCandidate(candidate);
        first.setJobOffer(offer);
        applicationRepository.saveAndFlush(first);

        Application duplicate = new Application();
        duplicate.setCandidate(candidate);
        duplicate.setJobOffer(offer);

        assertThatThrownBy(() -> applicationRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void removingEducationFromProfileDeletesIt() {
        CandidateProfile candidate = newCandidate("dina@test.com");
        Education education = new Education();
        education.setDegree("Licence");
        education.setInstitution("FST");
        candidate.addEducation(education);
        em.persist(candidate);
        em.flush();
        Long educationId = education.getId();

        candidate.removeEducation(education);
        em.flush();
        em.clear();

        assertThat(em.find(Education.class, educationId)).isNull();
    }

    @Test
    void ranksCandidatesByScoreDescending() {
        Company company = newCompany("Initech");
        JobOffer offer = newOffer(company, null, "Backend Java");

        saveScore(em.persist(newCandidate("low@test.com")), offer, "40.00");
        saveScore(em.persist(newCandidate("high@test.com")), offer, "92.50");
        saveScore(em.persist(newCandidate("mid@test.com")), offer, "71.25");
        em.flush();
        em.clear();

        List<MatchingResult> ranking = matchingRepository.findByJobOfferIdOrderByScoreDesc(offer.getId());

        assertThat(ranking).extracting(MatchingResult::getScore)
                .containsExactly(new BigDecimal("92.50"), new BigDecimal("71.25"), new BigDecimal("40.00"));
    }

    private CandidateProfile newCandidate(String email) {
        CandidateProfile candidate = new CandidateProfile();
        candidate.setUser(newUser(email, candidateRole));
        candidate.setEducationLevel(EducationLevel.BAC_PLUS_5);
        candidate.setYearsOfExperience(1);
        return candidate;
    }

    private RecruiterProfile newRecruiter(String email, Company company) {
        RecruiterProfile recruiter = new RecruiterProfile();
        recruiter.setUser(newUser(email, recruiterRole));
        recruiter.setCompany(company);
        return em.persist(recruiter);
    }

    private User newUser(String email, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("not-a-real-hash");
        user.setFirstName("Test");
        user.setLastName("User");
        user.setRole(role);
        return em.persist(user);
    }

    private Company newCompany(String name) {
        Company company = new Company();
        company.setName(name);
        return em.persist(company);
    }

    private JobOffer newOffer(Company company, RecruiterProfile recruiter, String title) {
        JobOffer offer = new JobOffer();
        offer.setTitle(title);
        offer.setDescription("Description de l'offre " + title);
        offer.setContractType(ContractType.CDI);
        offer.setMinExperienceYears(2);
        offer.setEducationLevel(EducationLevel.BAC_PLUS_5);
        offer.setStatus(JobOfferStatus.PUBLIEE);
        offer.setRecruiter(recruiter);
        company.addJobOffer(offer);
        return em.persist(offer);
    }

    private void saveScore(CandidateProfile candidate, JobOffer offer, String score) {
        MatchingResult result = new MatchingResult();
        result.setCandidate(candidate);
        result.setJobOffer(offer);
        result.setScore(new BigDecimal(score));
        result.setAlgorithmVersion("test");
        result.setComputedAt(LocalDateTime.now());
        em.persist(result);
    }
}
