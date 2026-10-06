package com.smartrecruit.joboffer.service;

import com.smartrecruit.common.dto.PageResponse;
import com.smartrecruit.common.model.EducationLevel;
import com.smartrecruit.common.exception.BadRequestException;
import com.smartrecruit.common.exception.ResourceNotFoundException;
import com.smartrecruit.company.entity.Company;
import com.smartrecruit.joboffer.dto.JobOfferRequest;
import com.smartrecruit.joboffer.dto.JobOfferResponse;
import com.smartrecruit.joboffer.dto.JobSearchCriteria;
import com.smartrecruit.joboffer.entity.JobOffer;
import com.smartrecruit.joboffer.entity.JobOfferStatus;
import com.smartrecruit.joboffer.repository.JobOfferRepository;
import com.smartrecruit.joboffer.repository.JobOfferSpecifications;
import com.smartrecruit.matching.repository.MatchingResultRepository;
import com.smartrecruit.recruiter.entity.RecruiterProfile;
import com.smartrecruit.recruiter.service.RecruiterProfileService;
import com.smartrecruit.skill.entity.Skill;
import com.smartrecruit.skill.service.SkillService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.smartrecruit.common.util.Strings.blankToNull;

@Service
@RequiredArgsConstructor
public class JobOfferService {

    private static final int MAX_PAGE_SIZE = 50;

    private final JobOfferRepository jobOfferRepository;
    private final RecruiterProfileService recruiterProfileService;
    private final SkillService skillService;
    private final MatchingResultRepository matchingResultRepository;

    // ---------- Public ----------

    @Transactional(readOnly = true)
    public PageResponse<JobOfferResponse> searchPublished(JobSearchCriteria criteria, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.from(
                jobOfferRepository.findAll(JobOfferSpecifications.publishedMatching(criteria), pageable),
                JobOfferResponse::forPublic);
    }

    /** Détail public : une offre en brouillon ou fermée n'est pas visible. */
    @Transactional(readOnly = true)
    public JobOfferResponse getPublished(Long id) {
        return JobOfferResponse.forPublic(getPublishedEntity(id));
    }

    @Transactional(readOnly = true)
    public JobOffer getPublishedEntity(Long id) {
        return jobOfferRepository.findById(id)
                .filter(o -> o.getStatus() == JobOfferStatus.PUBLIEE)
                .orElseThrow(() -> new ResourceNotFoundException("Offre introuvable"));
    }

    // ---------- Recruteur (offres de son entreprise) ----------

    @Transactional(readOnly = true)
    public List<JobOfferResponse> findMine(Long userId) {
        Company company = recruiterProfileService.requireCompany(userId);
        return jobOfferRepository.findByCompanyIdOrderByCreatedAtDesc(company.getId()).stream()
                .map(JobOfferResponse::forOwner).toList();
    }

    @Transactional(readOnly = true)
    public JobOfferResponse getMine(Long userId, Long offerId) {
        return JobOfferResponse.forOwner(getOwnedEntity(userId, offerId));
    }

    @Transactional
    public JobOfferResponse create(Long userId, JobOfferRequest request) {
        RecruiterProfile recruiter = recruiterProfileService.getByUserId(userId);
        Company company = recruiterProfileService.requireCompany(userId);
        JobOffer offer = new JobOffer();
        offer.setRecruiter(recruiter);
        apply(offer, request);
        offer.setStatus(request.status() != null ? request.status() : JobOfferStatus.BROUILLON);
        company.addJobOffer(offer);
        jobOfferRepository.save(offer);
        return JobOfferResponse.forOwner(offer);
    }

    @Transactional
    public JobOfferResponse update(Long userId, Long offerId, JobOfferRequest request) {
        JobOffer offer = getOwnedEntity(userId, offerId);
        MatchingCriteria before = MatchingCriteria.of(offer);
        apply(offer, request);
        if (request.status() != null) {
            offer.setStatus(request.status());
        }
        if (!before.equals(MatchingCriteria.of(offer))) {
            matchingResultRepository.deleteByJobOfferId(offer.getId());
        }
        return JobOfferResponse.forOwner(offer);
    }

    /** Critères utilisés par le matching : s'ils changent, les scores enregistrés pour l'offre sont supprimés. */
    private record MatchingCriteria(Set<Long> skillIds, int minExperienceYears, EducationLevel educationLevel) {

        static MatchingCriteria of(JobOffer offer) {
            return new MatchingCriteria(
                    offer.getRequiredSkills().stream().map(Skill::getId).collect(Collectors.toSet()),
                    offer.getMinExperienceYears(), offer.getEducationLevel());
        }
    }

    @Transactional
    public JobOfferResponse updateStatus(Long userId, Long offerId, JobOfferStatus status) {
        JobOffer offer = getOwnedEntity(userId, offerId);
        offer.setStatus(status);
        return JobOfferResponse.forOwner(offer);
    }

    @Transactional
    public void delete(Long userId, Long offerId) {
        JobOffer offer = getOwnedEntity(userId, offerId);
        offer.getCompany().getJobOffers().remove(offer);
        jobOfferRepository.delete(offer);
    }

    /**
     * Offre appartenant à l'entreprise du recruteur. Une offre d'une autre entreprise est
     * signalée comme introuvable (404) pour ne pas révéler son existence.
     */
    @Transactional(readOnly = true)
    public JobOffer getOwnedEntity(Long userId, Long offerId) {
        Company company = recruiterProfileService.requireCompany(userId);
        return jobOfferRepository.findById(offerId)
                .filter(o -> o.getCompany().getId().equals(company.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Offre introuvable"));
    }

    // ---------- Administration ----------

    @Transactional(readOnly = true)
    public List<JobOfferResponse> findAll(JobOfferStatus status) {
        List<JobOffer> offers = status == null
                ? jobOfferRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                : jobOfferRepository.findByStatus(status);
        return offers.stream().map(JobOfferResponse::forOwner).toList();
    }

    @Transactional
    public JobOfferResponse adminUpdateStatus(Long offerId, JobOfferStatus status) {
        JobOffer offer = getById(offerId);
        offer.setStatus(status);
        return JobOfferResponse.forOwner(offer);
    }

    @Transactional
    public void adminDelete(Long offerId) {
        JobOffer offer = getById(offerId);
        offer.getCompany().getJobOffers().remove(offer);
        jobOfferRepository.delete(offer);
    }

    private JobOffer getById(Long offerId) {
        return jobOfferRepository.findById(offerId)
                .orElseThrow(() -> new ResourceNotFoundException("Offre introuvable"));
    }

    private void apply(JobOffer offer, JobOfferRequest r) {
        if (r.salaryMin() != null && r.salaryMax() != null && r.salaryMax().compareTo(r.salaryMin()) < 0) {
            throw new BadRequestException("Le salaire maximum doit être supérieur ou égal au salaire minimum");
        }
        offer.setTitle(r.title().trim());
        offer.setDescription(r.description().trim());
        offer.setLocation(blankToNull(r.location()));
        offer.setContractType(r.contractType());
        offer.setMinExperienceYears(r.minExperienceYears());
        offer.setEducationLevel(r.educationLevel());
        offer.setSalaryMin(r.salaryMin());
        offer.setSalaryMax(r.salaryMax());
        offer.getRequiredSkills().clear();
        offer.getRequiredSkills().addAll(skillService.resolve(r.requiredSkills()));
    }
}
