package com.smartrecruit.matching.service;

import com.smartrecruit.ai.AiServiceClient;
import com.smartrecruit.ai.AiServiceRequestException;
import com.smartrecruit.ai.AiServiceUnavailableException;
import com.smartrecruit.application.entity.Application;
import com.smartrecruit.application.repository.ApplicationRepository;
import com.smartrecruit.application.service.ApplicationService;
import com.smartrecruit.candidate.entity.CandidateProfile;
import com.smartrecruit.candidate.repository.CandidateProfileRepository;
import com.smartrecruit.candidate.service.CandidateProfileService;
import com.smartrecruit.common.exception.BadRequestException;
import com.smartrecruit.common.exception.ResourceNotFoundException;
import com.smartrecruit.cv.dto.CvParseResult;
import com.smartrecruit.cv.entity.Cv;
import com.smartrecruit.cv.service.CvService;
import com.smartrecruit.joboffer.entity.JobOffer;
import com.smartrecruit.joboffer.entity.JobOfferStatus;
import com.smartrecruit.joboffer.repository.JobOfferRepository;
import com.smartrecruit.joboffer.service.JobOfferService;
import com.smartrecruit.matching.dto.AiMatchingResult;
import com.smartrecruit.matching.dto.CandidateMatchingResponse;
import com.smartrecruit.matching.dto.CandidateMatchingResponse.Status;
import com.smartrecruit.matching.dto.MatchingRefreshResponse;
import com.smartrecruit.matching.dto.MatchingRequest;
import com.smartrecruit.matching.dto.MatchingResultResponse;
import com.smartrecruit.matching.entity.MatchingResult;
import com.smartrecruit.matching.repository.MatchingResultRepository;
import com.smartrecruit.skill.service.SkillService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Matching CV ↔ offre : CV analysé + offre -> FastAPI (module matching) -> score expliqué -> MySQL -> Angular.
 *
 * <ul>
 *   <li>Le candidat est décrit uniquement par l'analyse de son CV (compétences détectées, durée d'expérience,
 *       niveau d'études) : aucune donnée d'identité n'est envoyée au service IA.</li>
 *   <li>Le score est recalculé à chaque consultation et enregistré (un seul résultat par couple candidat/offre).</li>
 *   <li>L'appel au service IA se fait hors transaction ; sa réponse est validée avant enregistrement.</li>
 *   <li>Le score est une aide pour le recruteur, jamais une décision automatique.</li>
 * </ul>
 */
@Slf4j
@Service
public class MatchingService {

    static final String SCORE_PATH = "/api/matching/score";
    static final int MAX_CANDIDATE_SKILLS = 400;

    static final String COMPUTED_MESSAGE = "Score calculé à partir de votre CV analysé. Il s'agit d'une aide "
            + "indicative, pas d'une décision de recrutement.";
    static final String NO_CV_MESSAGE = "Importez votre CV pour connaître votre compatibilité avec cette offre.";
    static final String NOT_ANALYZED_MESSAGE = "Votre CV n'a pas encore été analysé : relancez l'analyse depuis "
            + "la page « Mon CV » pour connaître votre compatibilité.";
    static final String AI_DOWN_MESSAGE = "Le service d'analyse est momentanément indisponible : le score n'a pas "
            + "pu être recalculé.";
    static final String AI_DOWN_WITH_PREVIOUS = AI_DOWN_MESSAGE + " Le dernier score enregistré est affiché.";
    static final String NO_REQUIRED_SKILL_MESSAGE = "Cette offre n'indique aucune compétence requise : "
            + "le score de compatibilité ne peut pas être calculé.";

    private final MatchingResultRepository matchingResultRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final CandidateProfileService candidateProfileService;
    private final JobOfferRepository jobOfferRepository;
    private final JobOfferService jobOfferService;
    private final ApplicationRepository applicationRepository;
    private final ApplicationService applicationService;
    private final CvService cvService;
    private final AiServiceClient aiClient;
    private final MatchingResultMapper mapper;
    private final TransactionTemplate transaction;
    private final TransactionTemplate readOnlyTransaction;

    public MatchingService(MatchingResultRepository matchingResultRepository,
                           CandidateProfileRepository candidateProfileRepository,
                           CandidateProfileService candidateProfileService, JobOfferRepository jobOfferRepository,
                           JobOfferService jobOfferService, ApplicationRepository applicationRepository,
                           ApplicationService applicationService, CvService cvService, AiServiceClient aiClient,
                           MatchingResultMapper mapper, PlatformTransactionManager transactionManager) {
        this.matchingResultRepository = matchingResultRepository;
        this.candidateProfileRepository = candidateProfileRepository;
        this.candidateProfileService = candidateProfileService;
        this.jobOfferRepository = jobOfferRepository;
        this.jobOfferService = jobOfferService;
        this.applicationRepository = applicationRepository;
        this.applicationService = applicationService;
        this.cvService = cvService;
        this.aiClient = aiClient;
        this.mapper = mapper;
        this.transaction = new TransactionTemplate(transactionManager);
        this.readOnlyTransaction = new TransactionTemplate(transactionManager);
        this.readOnlyTransaction.setReadOnly(true);
    }

    // ---------- Candidat ----------

    /**
     * Compatibilité du candidat connecté avec une offre publiée (ou à laquelle il a postulé), recalculée à
     * partir de son CV analysé. Service IA indisponible : le dernier score enregistré est renvoyé s'il existe.
     */
    public CandidateMatchingResponse forCandidate(Long userId, Long offerId) {
        CandidateRequest prepared = readOnlyTransaction.execute(status -> prepareCandidate(userId, offerId));
        if (prepared.blocked() != null) {
            return new CandidateMatchingResponse(prepared.blocked(),
                    prepared.blocked() == Status.CV_ABSENT ? NO_CV_MESSAGE : NOT_ANALYZED_MESSAGE, null);
        }
        AiMatchingResult result;
        try {
            result = score(prepared.request());
        } catch (AiServiceUnavailableException | AiServiceRequestException ex) {
            MatchingResultResponse previous = readOnlyTransaction.execute(status -> stored(prepared.candidateId(),
                    offerId));
            log.warn("Matching impossible (candidat {}, offre {}) : service IA indisponible", prepared.candidateId(),
                    offerId);
            return new CandidateMatchingResponse(Status.IA_INDISPONIBLE,
                    previous == null ? AI_DOWN_MESSAGE : AI_DOWN_WITH_PREVIOUS, previous);
        }
        MatchingResultResponse saved = save(prepared.candidateId(), offerId, result);
        return new CandidateMatchingResponse(Status.CALCULE, COMPUTED_MESSAGE, saved);
    }

    /** Calcul après une candidature : ne doit jamais faire échouer la candidature elle-même. */
    public void computeAfterApplication(Long userId, Long offerId) {
        try {
            CandidateMatchingResponse response = forCandidate(userId, offerId);
            log.info("Matching après candidature (offre {}) : {}", offerId, response.status());
        } catch (RuntimeException ex) {
            log.warn("Matching après candidature impossible (offre {}) : {}", offerId, ex.getClass().getSimpleName());
        }
    }

    private CandidateRequest prepareCandidate(Long userId, Long offerId) {
        CandidateProfile profile = candidateProfileService.getByUserId(userId);
        JobOffer offer = jobOfferRepository.findById(offerId)
                .filter(o -> o.getStatus() == JobOfferStatus.PUBLIEE
                        || applicationRepository.existsByCandidateIdAndJobOfferId(profile.getId(), o.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Offre introuvable"));
        MatchingRequest.Offer offerInput = offerInput(offer);
        Cv cv = profile.getCv();
        if (cv == null) {
            return CandidateRequest.blocked(profile.getId(), Status.CV_ABSENT);
        }
        return candidateInput(cv)
                .map(c -> new CandidateRequest(profile.getId(), null, new MatchingRequest(c, offerInput)))
                .orElseGet(() -> CandidateRequest.blocked(profile.getId(), Status.CV_NON_ANALYSE));
    }

    // ---------- Recruteur ----------

    /**
     * Recalcule le score de chaque candidat de l'offre ayant un CV analysé, puis renvoie les candidatures classées.
     * Service IA indisponible : 503 (les scores déjà recalculés restent enregistrés).
     */
    public MatchingRefreshResponse refreshForOffer(Long userId, Long offerId) {
        RefreshPlan plan = readOnlyTransaction.execute(status -> prepareRefresh(userId, offerId));
        int computed = 0;
        for (CandidateInput candidate : plan.candidates()) {
            AiMatchingResult result = score(new MatchingRequest(candidate.input(), plan.offer()));
            save(candidate.candidateId(), offerId, result);
            computed++;
        }
        log.info("Scores recalculés pour l'offre {} : {} candidats, {} sans CV analysé", offerId, computed,
                plan.withoutAnalyzedCv());
        return new MatchingRefreshResponse(computed, plan.withoutAnalyzedCv(),
                applicationService.rankedApplications(offerId));
    }

    private RefreshPlan prepareRefresh(Long userId, Long offerId) {
        JobOffer offer = jobOfferService.getOwnedEntity(userId, offerId);
        MatchingRequest.Offer offerInput = offerInput(offer);
        List<CandidateInput> candidates = new ArrayList<>();
        int withoutAnalyzedCv = 0;
        for (Application application : applicationRepository.findByJobOfferIdOrderByCreatedAtDesc(offer.getId())) {
            CandidateProfile profile = application.getCandidate();
            Optional<MatchingRequest.Candidate> input = candidateInput(profile.getCv());
            if (input.isPresent()) {
                candidates.add(new CandidateInput(profile.getId(), input.get()));
            } else {
                withoutAnalyzedCv++;
            }
        }
        return new RefreshPlan(offerInput, candidates, withoutAnalyzedCv);
    }

    // ---------- Commun ----------

    private AiMatchingResult score(MatchingRequest request) {
        return aiClient.post(SCORE_PATH, request, AiMatchingResult.class);
    }

    private Optional<MatchingRequest.Candidate> candidateInput(Cv cv) {
        return cvService.analysisOf(cv).map(analysis -> new MatchingRequest.Candidate(
                Stream.concat(analysis.technologies().stream(), analysis.skills().stream())
                        .map(CvParseResult.DetectedSkill::name)
                        .distinct()
                        .limit(MAX_CANDIDATE_SKILLS)
                        .toList(),
                analysis.totalExperienceMonths(), analysis.highestEducationLevel()));
    }

    private static MatchingRequest.Offer offerInput(JobOffer offer) {
        if (offer.getRequiredSkills().isEmpty()) {
            throw new BadRequestException(NO_REQUIRED_SKILL_MESSAGE);
        }
        return new MatchingRequest.Offer(SkillService.names(offer.getRequiredSkills()),
                offer.getMinExperienceYears(), offer.getEducationLevel());
    }

    /** Enregistre (ou remplace) le résultat du couple candidat/offre ; une seconde tentative en cas de course. */
    private MatchingResultResponse save(Long candidateId, Long offerId, AiMatchingResult result) {
        MatchingResultResponse saved;
        try {
            saved = transaction.execute(status -> upsert(candidateId, offerId, result));
        } catch (DataIntegrityViolationException ex) {
            saved = transaction.execute(status -> upsert(candidateId, offerId, result));
        }
        log.info("Matching enregistré (candidat {}, offre {}) : score {}, version {}", candidateId, offerId,
                result.score(), result.algorithmVersion());
        return saved;
    }

    private MatchingResultResponse upsert(Long candidateId, Long offerId, AiMatchingResult result) {
        CandidateProfile profile = candidateProfileRepository.findById(candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("Candidat introuvable"));
        MatchingResult entity = matchingResultRepository.findByCandidateIdAndJobOfferId(candidateId, offerId)
                .orElseGet(() -> {
                    MatchingResult created = new MatchingResult();
                    created.setCandidate(profile);
                    created.setJobOffer(jobOfferRepository.getReferenceById(offerId));
                    return created;
                });
        mapper.apply(entity, result);
        return mapper.toResponse(matchingResultRepository.saveAndFlush(entity), profile.getCv());
    }

    private MatchingResultResponse stored(Long candidateId, Long offerId) {
        return matchingResultRepository.findByCandidateIdAndJobOfferId(candidateId, offerId)
                .map(m -> mapper.toResponse(m, m.getCandidate().getCv()))
                .orElse(null);
    }

    private record CandidateRequest(Long candidateId, Status blocked, MatchingRequest request) {

        static CandidateRequest blocked(Long candidateId, Status status) {
            return new CandidateRequest(candidateId, status, null);
        }
    }

    private record CandidateInput(Long candidateId, MatchingRequest.Candidate input) {
    }

    private record RefreshPlan(MatchingRequest.Offer offer, List<CandidateInput> candidates, int withoutAnalyzedCv) {
    }
}
