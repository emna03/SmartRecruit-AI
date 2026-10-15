package com.smartrecruit.cv.service;

import com.smartrecruit.ai.AiServiceClient;
import com.smartrecruit.ai.AiServiceRequestException;
import com.smartrecruit.ai.AiServiceUnavailableException;
import com.smartrecruit.candidate.dto.CandidateProfileResponse;
import com.smartrecruit.candidate.entity.CandidateProfile;
import com.smartrecruit.candidate.entity.Education;
import com.smartrecruit.candidate.entity.Experience;
import com.smartrecruit.candidate.service.CandidateProfileService;
import com.smartrecruit.common.exception.BadRequestException;
import com.smartrecruit.common.exception.ResourceNotFoundException;
import com.smartrecruit.cv.dto.CvImportRequest;
import com.smartrecruit.cv.dto.CvImportResponse;
import com.smartrecruit.cv.dto.CvParseResult;
import com.smartrecruit.cv.dto.CvResponse;
import com.smartrecruit.cv.dto.ProfessionalSummaryAiRequest;
import com.smartrecruit.cv.dto.ProfessionalSummaryResponse;
import com.smartrecruit.cv.entity.Cv;
import com.smartrecruit.cv.entity.CvParsingStatus;
import com.smartrecruit.cv.repository.CvRepository;
import com.smartrecruit.matching.repository.MatchingResultRepository;
import com.smartrecruit.skill.entity.Skill;
import com.smartrecruit.skill.service.SkillService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * CV du candidat connecté : Angular -> Spring Boot -> FastAPI (CV Parser) -> MySQL -> Angular.
 *
 * <ul>
 *   <li>Le fichier est validé (non vide, taille, signature PDF) puis stocké sous un nom aléatoire.</li>
 *   <li>L'appel au service IA se fait hors transaction (il peut durer plusieurs secondes).</li>
 *   <li>PDF refusé par le parser (invalide, chiffré, sans texte) : le nouveau fichier est supprimé, l'ancien CV
 *       est conservé, HTTP 422 avec le message du parser.</li>
 *   <li>Service IA indisponible ou trop lent : le CV est enregistré avec le statut ECHEC et peut être réanalysé.</li>
 *   <li>Seuls des compteurs et identifiants sont journalisés, jamais le contenu du CV.</li>
 * </ul>
 */
@Slf4j
@Service
public class CvService {

    static final String PARSE_PATH = "/api/cv/parse";
    static final String SUMMARY_PATH = "/api/llm/professional-summary";
    static final String NO_CV_MESSAGE = "Aucun CV importé";
    static final String NOT_ANALYZED_MESSAGE = "Le CV n'a pas encore été analysé : relancez l'analyse avant l'import.";
    static final String SUMMARY_REQUIRES_ANALYSIS =
            "Le CV n'a pas encore été analysé : relancez l'analyse avant de générer un résumé IA.";
    static final int MAX_PROFILE_SKILLS = 50;

    private final CvRepository cvRepository;
    private final MatchingResultRepository matchingResultRepository;
    private final CandidateProfileService profileService;
    private final SkillService skillService;
    private final AiServiceClient aiClient;
    private final CvStorage storage;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transaction;
    private final TransactionTemplate readOnlyTransaction;
    private final DataSize maxFileSize;

    public CvService(CvRepository cvRepository, MatchingResultRepository matchingResultRepository,
                     CandidateProfileService profileService, SkillService skillService,
                     AiServiceClient aiClient, CvStorage storage, ObjectMapper objectMapper,
                     PlatformTransactionManager transactionManager,
                     @Value("${spring.servlet.multipart.max-file-size}") DataSize maxFileSize) {
        this.cvRepository = cvRepository;
        this.matchingResultRepository = matchingResultRepository;
        this.profileService = profileService;
        this.skillService = skillService;
        this.aiClient = aiClient;
        this.storage = storage;
        this.objectMapper = objectMapper;
        this.transaction = new TransactionTemplate(transactionManager);
        this.readOnlyTransaction = new TransactionTemplate(transactionManager);
        this.readOnlyTransaction.setReadOnly(true);
        this.maxFileSize = maxFileSize;
    }

    public CvResponse get(Long userId) {
        return readOnlyTransaction.execute(status -> toResponse(findCv(userId)));
    }

    /** Premier import ou remplacement du CV, suivi de son analyse. */
    public CvResponse upload(Long userId, MultipartFile file) {
        CvFileValidator.ValidatedPdf pdf = CvFileValidator.validate(file, maxFileSize);
        profileService.getByUserId(userId);
        String storedFileName = storage.store(pdf.content());

        Analysis analysis;
        try {
            analysis = analyze(pdf.content());
        } catch (AiServiceRequestException ex) {
            storage.delete(storedFileName);
            log.info("CV refusé par le parser (utilisateur {}, {} octets) : HTTP {}", userId, pdf.content().length,
                    ex.getUpstreamStatus());
            throw ex;
        }

        Saved saved;
        try {
            saved = transaction.execute(status -> {
                CandidateProfile profile = profileService.getByUserId(userId);
                Cv cv = profile.getCv();
                String previous = null;
                if (cv == null) {
                    cv = new Cv();
                    profile.attachCv(cv);
                } else {
                    previous = cv.getStoredFileName();
                }
                cv.setOriginalFileName(pdf.originalFileName());
                cv.setStoredFileName(storedFileName);
                cv.setContentType(CvFileValidator.PDF_CONTENT_TYPE);
                cv.setFileSize(pdf.content().length);
                apply(cv, analysis);
                return new Saved(toResponse(cvRepository.saveAndFlush(cv)), previous);
            });
        } catch (RuntimeException ex) {
            storage.delete(storedFileName);
            throw ex;
        }
        if (saved.previousFile() != null && !saved.previousFile().equals(storedFileName)) {
            storage.delete(saved.previousFile());
        }
        logResult("CV importé", userId, saved.response());
        return saved.response();
    }

    /** Relance l'analyse du CV déjà stocké (après une indisponibilité du service IA par exemple). */
    public CvResponse reanalyze(Long userId) {
        String storedFileName = readOnlyTransaction.execute(status -> findCv(userId).getStoredFileName());
        byte[] content = storage.read(storedFileName);
        Analysis analysis;
        try {
            analysis = analyze(content);
        } catch (AiServiceRequestException ex) {
            analysis = Analysis.failed(ex.getMessage());
        }
        Analysis outcome = analysis;
        CvResponse response = transaction.execute(status -> {
            Cv cv = findCv(userId);
            apply(cv, outcome);
            return toResponse(cvRepository.saveAndFlush(cv));
        });
        logResult("CV réanalysé", userId, response);
        return response;
    }

    public void delete(Long userId) {
        String storedFileName = transaction.execute(status -> {
            CandidateProfile profile = profileService.getByUserId(userId);
            Cv cv = profile.getCv();
            if (cv == null) {
                throw new ResourceNotFoundException(NO_CV_MESSAGE);
            }
            String name = cv.getStoredFileName();
            matchingResultRepository.deleteByCandidateId(profile.getId());
            profile.attachCv(null);
            cvRepository.delete(cv);
            cvRepository.flush();
            return name;
        });
        storage.delete(storedFileName);
        log.info("CV supprimé (utilisateur {})", userId);
    }

    /**
     * Ajoute au profil les éléments extraits choisis par le candidat. Seules les entrées complètes sont reprises
     * (formation : diplôme et établissement ; expérience : poste, entreprise et date de début) et les doublons
     * sont ignorés. Niveau d'études et années d'expérience ne sont renseignés que s'ils sont vides.
     */
    public CvImportResponse importIntoProfile(Long userId, CvImportRequest request) {
        CvImportResponse response = transaction.execute(status -> {
            CandidateProfile profile = profileService.getByUserId(userId);
            Cv cv = profile.getCv();
            if (cv == null) {
                throw new ResourceNotFoundException(NO_CV_MESSAGE);
            }
            CvParseResult analysis = readAnalysis(cv);
            if (cv.getParsingStatus() != CvParsingStatus.TERMINE || analysis == null) {
                throw new BadRequestException(NOT_ANALYZED_MESSAGE);
            }
            int skipped = 0;
            int skillsAdded = 0;
            if (request.includeSkills()) {
                for (Skill skill : cv.getExtractedSkills()) {
                    boolean known = profile.getSkills().stream().anyMatch(s -> Objects.equals(s.getId(), skill.getId()));
                    if (known) {
                        continue;
                    }
                    if (profile.getSkills().size() >= MAX_PROFILE_SKILLS) {
                        skipped++;
                    } else {
                        profile.getSkills().add(skill);
                        skillsAdded++;
                    }
                }
            }
            int educationsAdded = 0;
            if (request.includeEducations()) {
                for (CvParseResult.EducationEntry entry : analysis.educations()) {
                    if (addEducation(profile, entry)) {
                        educationsAdded++;
                    } else {
                        skipped++;
                    }
                }
            }
            int experiencesAdded = 0;
            if (request.includeExperiences()) {
                for (CvParseResult.ExperienceEntry entry : analysis.experiences()) {
                    if (addExperience(profile, entry)) {
                        experiencesAdded++;
                    } else {
                        skipped++;
                    }
                }
            }
            boolean levelSet = request.includeEducations() && profile.getEducationLevel() == null
                    && analysis.highestEducationLevel() != null;
            if (levelSet) {
                profile.setEducationLevel(analysis.highestEducationLevel());
            }
            boolean yearsSet = request.includeExperiences() && profile.getYearsOfExperience() == null
                    && analysis.totalExperienceMonths() != null;
            if (yearsSet) {
                profile.setYearsOfExperience(analysis.totalExperienceMonths() / 12);
            }
            cvRepository.flush();
            return new CvImportResponse(skillsAdded, educationsAdded, experiencesAdded, skipped, levelSet, yearsSet,
                    CandidateProfileResponse.from(profile));
        });
        log.info("Import du CV dans le profil (utilisateur {}) : {} compétences, {} formations, {} expériences, "
                        + "{} ignorés", userId, response.skillsAdded(), response.educationsAdded(),
                response.experiencesAdded(), response.skipped());
        return response;
    }

    private Analysis analyze(byte[] content) {
        try {
            return Analysis.succeeded(aiClient.postBinary(PARSE_PATH, content, MediaType.APPLICATION_PDF,
                    CvParseResult.class));
        } catch (AiServiceUnavailableException ex) {
            return Analysis.failed(ex.getMessage());
        }
    }

    private void apply(Cv cv, Analysis analysis) {
        cv.getExtractedSkills().clear();
        if (analysis.result() == null) {
            cv.setParsingStatus(CvParsingStatus.ECHEC);
            cv.setParsingError(analysis.error());
            cv.setParsedAt(null);
            cv.setExtractedText(null);
            cv.setExtractedData(null);
            return;
        }
        CvParseResult result = analysis.result();
        cv.setParsingStatus(CvParsingStatus.TERMINE);
        cv.setParsingError(null);
        cv.setParsedAt(LocalDateTime.now(ZoneOffset.UTC));
        cv.setExtractedText(result.text());
        cv.setExtractedData(objectMapper.writeValueAsString(result.withoutText()));
        Map<String, String> categories = new LinkedHashMap<>();
        result.technologies().forEach(s -> categories.putIfAbsent(s.name(), s.category()));
        result.skills().forEach(s -> categories.putIfAbsent(s.name(), s.category()));
        cv.getExtractedSkills().addAll(skillService.resolveCategorized(categories));
    }

    private Cv findCv(Long userId) {
        Cv cv = profileService.getByUserId(userId).getCv();
        if (cv == null) {
            throw new ResourceNotFoundException(NO_CV_MESSAGE);
        }
        return cv;
    }

    private CvResponse toResponse(Cv cv) {
        return new CvResponse(cv.getId(), cv.getOriginalFileName(), cv.getFileSize(), cv.getParsingStatus(),
                cv.getParsingError(), cv.getParsedAt(), cv.getUpdatedAt(), readAnalysis(cv));
    }

    /** Analyse validée du CV, ou vide s'il n'est pas (ou plus) analysé. */
    public Optional<CvParseResult> analysisOf(Cv cv) {
        if (cv == null || cv.getParsingStatus() != CvParsingStatus.TERMINE) {
            return Optional.empty();
        }
        return Optional.ofNullable(readAnalysis(cv));
    }

    /**
     * Résumé professionnel généré par le LLM à partir des données structurées déjà extraites.
     * Le PDF et l'identité du candidat ne quittent jamais Spring Boot.
     */
    public ProfessionalSummaryResponse generateProfessionalSummary(Long userId) {
        CvParseResult analysis = readOnlyTransaction.execute(status -> {
            Cv cv = findCv(userId);
            if (cv.getParsingStatus() != CvParsingStatus.TERMINE) {
                throw new BadRequestException(SUMMARY_REQUIRES_ANALYSIS);
            }
            CvParseResult result = readAnalysis(cv);
            if (result == null) {
                throw new BadRequestException(SUMMARY_REQUIRES_ANALYSIS);
            }
            return result;
        });
        ProfessionalSummaryAiRequest request = toLlmRequest(analysis);
        ProfessionalSummaryResponse response = aiClient.post(SUMMARY_PATH, request, ProfessionalSummaryResponse.class);
        log.info("Résumé LLM généré (utilisateur {}) : modèle={}, mots-clés={}", userId, response.model(),
                response.keywords().size());
        return response;
    }

    static ProfessionalSummaryAiRequest toLlmRequest(CvParseResult analysis) {
        List<String> skills = analysis.skills().stream().map(CvParseResult.DetectedSkill::name).distinct().toList();
        List<String> technologies = analysis.technologies().stream().map(CvParseResult.DetectedSkill::name)
                .distinct().toList();
        List<ProfessionalSummaryAiRequest.Education> educations = analysis.educations().stream()
                .map(e -> new ProfessionalSummaryAiRequest.Education(
                        e.degree(), e.fieldOfStudy(), e.institution(),
                        e.level() == null ? null : e.level().name()))
                .toList();
        List<ProfessionalSummaryAiRequest.Experience> experiences = analysis.experiences().stream()
                .map(e -> new ProfessionalSummaryAiRequest.Experience(e.jobTitle(), e.company(), e.durationMonths()))
                .toList();
        List<ProfessionalSummaryAiRequest.Language> languages = analysis.languages().stream()
                .map(l -> new ProfessionalSummaryAiRequest.Language(l.name(), l.level(), l.cefr()))
                .toList();
        String highest = analysis.highestEducationLevel() == null ? null : analysis.highestEducationLevel().name();
        return new ProfessionalSummaryAiRequest(skills, technologies, educations, experiences, languages,
                analysis.totalExperienceMonths(), highest);
    }

    private CvParseResult readAnalysis(Cv cv) {
        if (cv.getExtractedData() == null) {
            return null;
        }
        try {
            return objectMapper.readValue(cv.getExtractedData(), CvParseResult.class);
        } catch (JacksonException e) {
            log.error("Données extraites illisibles pour le CV {} : {}", cv.getId(), e.getClass().getSimpleName());
            return null;
        }
    }

    private static boolean addEducation(CandidateProfile profile, CvParseResult.EducationEntry entry) {
        LocalDate start = toDate(entry.startDate());
        LocalDate end = toDate(entry.endDate());
        if (entry.institution() == null || (start != null && end != null && end.isBefore(start))) {
            return false;
        }
        boolean duplicate = profile.getEducations().stream().anyMatch(e ->
                same(e.getDegree(), entry.degree()) && same(e.getInstitution(), entry.institution()));
        if (duplicate) {
            return false;
        }
        Education education = new Education();
        education.setDegree(entry.degree());
        education.setFieldOfStudy(entry.fieldOfStudy());
        education.setInstitution(entry.institution());
        education.setStartDate(start);
        education.setEndDate(end);
        profile.addEducation(education);
        return true;
    }

    private static boolean addExperience(CandidateProfile profile, CvParseResult.ExperienceEntry entry) {
        LocalDate start = toDate(entry.startDate());
        LocalDate end = entry.current() ? null : toDate(entry.endDate());
        if (entry.company() == null || start == null || (end != null && end.isBefore(start))) {
            return false;
        }
        boolean duplicate = profile.getExperiences().stream().anyMatch(e ->
                same(e.getJobTitle(), entry.jobTitle()) && same(e.getCompanyName(), entry.company())
                        && Objects.equals(e.getStartDate(), start));
        if (duplicate) {
            return false;
        }
        Experience experience = new Experience();
        experience.setJobTitle(entry.jobTitle());
        experience.setCompanyName(entry.company());
        experience.setLocation(entry.location());
        experience.setStartDate(start);
        experience.setEndDate(end);
        experience.setDescription(entry.description());
        profile.addExperience(experience);
        return true;
    }

    /** « 2023 » -> 2023-01-01, « 2023-03 » -> 2023-03-01 (le jour, et parfois le mois, ne figurent pas sur un CV). */
    static LocalDate toDate(String partial) {
        if (partial == null) {
            return null;
        }
        String[] parts = partial.split("-");
        return LocalDate.of(Integer.parseInt(parts[0]), parts.length > 1 ? Integer.parseInt(parts[1]) : 1, 1);
    }

    private static boolean same(String a, String b) {
        Function<String, String> key = s -> s == null ? "" : s.strip().toLowerCase(Locale.ROOT);
        return key.apply(a).equals(key.apply(b));
    }

    private static void logResult(String action, Long userId, CvResponse response) {
        CvParseResult analysis = response.analysis();
        if (analysis == null) {
            log.warn("{} sans analyse (utilisateur {}, CV {}) : {}", action, userId, response.id(),
                    response.parsingError());
            return;
        }
        log.info("{} (utilisateur {}, CV {}) : {} technologies, {} compétences, {} formations, {} expériences, "
                        + "{} langues", action, userId, response.id(), analysis.technologies().size(),
                analysis.skills().size(), analysis.educations().size(), analysis.experiences().size(),
                analysis.languages().size());
    }

    private record Analysis(CvParseResult result, String error) {

        static Analysis succeeded(CvParseResult result) {
            return new Analysis(result, null);
        }

        static Analysis failed(String error) {
            return new Analysis(null, error);
        }
    }

    private record Saved(CvResponse response, String previousFile) {
    }
}
