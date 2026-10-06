package com.smartrecruit.application.service;

import com.smartrecruit.application.dto.ApplicationDetailResponse;
import com.smartrecruit.application.dto.ApplyRequest;
import com.smartrecruit.application.dto.CandidateApplicationResponse;
import com.smartrecruit.application.dto.RecruiterApplicationResponse;
import com.smartrecruit.application.entity.Application;
import com.smartrecruit.application.entity.ApplicationStatus;
import com.smartrecruit.application.repository.ApplicationRepository;
import com.smartrecruit.candidate.entity.CandidateProfile;
import com.smartrecruit.candidate.service.CandidateProfileService;
import com.smartrecruit.common.exception.BadRequestException;
import com.smartrecruit.common.exception.ConflictException;
import com.smartrecruit.common.exception.ResourceNotFoundException;
import com.smartrecruit.company.entity.Company;
import com.smartrecruit.joboffer.entity.JobOffer;
import com.smartrecruit.joboffer.entity.JobOfferStatus;
import com.smartrecruit.joboffer.repository.JobOfferRepository;
import com.smartrecruit.joboffer.service.JobOfferService;
import com.smartrecruit.matching.dto.MatchingResultResponse;
import com.smartrecruit.matching.entity.MatchingResult;
import com.smartrecruit.matching.repository.MatchingResultRepository;
import com.smartrecruit.matching.service.MatchingResultMapper;
import com.smartrecruit.recruiter.service.RecruiterProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.smartrecruit.common.util.Strings.blankToNull;

@Service
@RequiredArgsConstructor
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final JobOfferRepository jobOfferRepository;
    private final CandidateProfileService candidateProfileService;
    private final RecruiterProfileService recruiterProfileService;
    private final JobOfferService jobOfferService;
    private final MatchingResultRepository matchingResultRepository;
    private final MatchingResultMapper matchingMapper;

    private static final Comparator<RecruiterApplicationResponse> BEST_SCORE_FIRST = Comparator.comparing(
            (RecruiterApplicationResponse r) -> r.matching() == null ? null : r.matching().score(),
            Comparator.nullsLast(Comparator.reverseOrder()));

    // ---------- Candidat ----------

    @Transactional
    public CandidateApplicationResponse apply(Long userId, ApplyRequest request) {
        CandidateProfile candidate = candidateProfileService.getByUserId(userId);
        JobOffer offer = jobOfferRepository.findById(request.jobOfferId())
                .orElseThrow(() -> new ResourceNotFoundException("Offre introuvable"));
        if (offer.getStatus() != JobOfferStatus.PUBLIEE) {
            throw new BadRequestException("Cette offre n'accepte pas de candidatures");
        }
        if (applicationRepository.existsByCandidateIdAndJobOfferId(candidate.getId(), offer.getId())) {
            throw new ConflictException("Vous avez déjà postulé à cette offre");
        }
        Application application = new Application();
        application.setCandidate(candidate);
        application.setJobOffer(offer);
        application.setCoverLetter(blankToNull(request.coverLetter()));
        applicationRepository.save(application);
        offer.getApplications().add(application);
        return CandidateApplicationResponse.from(application);
    }

    @Transactional(readOnly = true)
    public List<CandidateApplicationResponse> findMine(Long userId) {
        CandidateProfile candidate = candidateProfileService.getByUserId(userId);
        return applicationRepository.findByCandidateIdOrderByCreatedAtDesc(candidate.getId()).stream()
                .map(CandidateApplicationResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CandidateApplicationResponse getMine(Long userId, Long applicationId) {
        CandidateProfile candidate = candidateProfileService.getByUserId(userId);
        return applicationRepository.findById(applicationId)
                .filter(a -> a.getCandidate().getId().equals(candidate.getId()))
                .map(CandidateApplicationResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Candidature introuvable"));
    }

    // ---------- Recruteur (candidatures aux offres de son entreprise) ----------

    /**
     * Candidatures d'une offre classées du meilleur score de compatibilité au moins bon ; les candidats sans score
     * (CV absent ou non analysé) viennent ensuite. À score égal, la candidature la plus récente d'abord.
     */
    @Transactional(readOnly = true)
    public List<RecruiterApplicationResponse> findForOffer(Long userId, Long offerId) {
        return rankedApplications(jobOfferService.getOwnedEntity(userId, offerId).getId());
    }

    /** Même classement, pour une offre dont l'appartenance au recruteur a déjà été vérifiée. */
    @Transactional(readOnly = true)
    public List<RecruiterApplicationResponse> rankedApplications(Long offerId) {
        Map<Long, MatchingResult> scores = matchingResultRepository.findByJobOfferIdOrderByScoreDesc(offerId)
                .stream().collect(Collectors.toMap(m -> m.getCandidate().getId(), Function.identity()));
        return applicationRepository.findByJobOfferIdOrderByCreatedAtDesc(offerId).stream()
                .map(a -> RecruiterApplicationResponse.from(a, matchingMapper.toResponse(
                        scores.get(a.getCandidate().getId()), a.getCandidate().getCv())))
                .sorted(BEST_SCORE_FIRST)
                .toList();
    }

    @Transactional(readOnly = true)
    public ApplicationDetailResponse getForRecruiter(Long userId, Long applicationId) {
        Application application = getOwnedByRecruiter(userId, applicationId);
        return ApplicationDetailResponse.from(application, matchingOf(application));
    }

    @Transactional
    public ApplicationDetailResponse updateStatus(Long userId, Long applicationId, ApplicationStatus status) {
        Application application = getOwnedByRecruiter(userId, applicationId);
        application.setStatus(status);
        return ApplicationDetailResponse.from(application, matchingOf(application));
    }

    private MatchingResultResponse matchingOf(Application application) {
        CandidateProfile candidate = application.getCandidate();
        return matchingResultRepository.findByCandidateIdAndJobOfferId(candidate.getId(),
                        application.getJobOffer().getId())
                .map(m -> matchingMapper.toResponse(m, candidate.getCv()))
                .orElse(null);
    }

    private Application getOwnedByRecruiter(Long userId, Long applicationId) {
        Company company = recruiterProfileService.requireCompany(userId);
        return applicationRepository.findById(applicationId)
                .filter(a -> a.getJobOffer().getCompany().getId().equals(company.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Candidature introuvable"));
    }
}
