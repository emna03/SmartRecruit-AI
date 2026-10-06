package com.smartrecruit.company.service;

import com.smartrecruit.common.exception.ConflictException;
import com.smartrecruit.common.exception.ResourceNotFoundException;
import com.smartrecruit.company.dto.CompanyRequest;
import com.smartrecruit.company.dto.CompanyResponse;
import com.smartrecruit.company.entity.Company;
import com.smartrecruit.company.repository.CompanyRepository;
import com.smartrecruit.recruiter.entity.RecruiterProfile;
import com.smartrecruit.recruiter.service.RecruiterProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.smartrecruit.common.util.Strings.blankToNull;

@Service
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final RecruiterProfileService recruiterProfileService;

    // ---------- Recruteur : son entreprise ----------

    @Transactional(readOnly = true)
    public CompanyResponse getMyCompany(Long userId) {
        Company company = recruiterProfileService.getByUserId(userId).getCompany();
        if (company == null) {
            throw new ResourceNotFoundException("Vous n'avez pas encore créé votre entreprise");
        }
        return CompanyResponse.from(company);
    }

    @Transactional
    public CompanyResponse createMyCompany(Long userId, CompanyRequest request) {
        RecruiterProfile recruiter = recruiterProfileService.getByUserId(userId);
        if (recruiter.getCompany() != null) {
            throw new ConflictException("Vous êtes déjà rattaché à une entreprise");
        }
        checkNameAvailable(request.name(), null);
        Company company = new Company();
        apply(company, request);
        companyRepository.save(company);
        recruiter.setCompany(company);
        company.getRecruiters().add(recruiter);
        return CompanyResponse.from(company);
    }

    @Transactional
    public CompanyResponse updateMyCompany(Long userId, CompanyRequest request) {
        Company company = recruiterProfileService.requireCompany(userId);
        return CompanyResponse.from(update(company, request));
    }

    // ---------- Administration ----------

    @Transactional(readOnly = true)
    public List<CompanyResponse> findAll() {
        return companyRepository.findAll(Sort.by("name")).stream().map(CompanyResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CompanyResponse findById(Long id) {
        return CompanyResponse.from(get(id));
    }

    @Transactional
    public CompanyResponse adminUpdate(Long id, CompanyRequest request) {
        return CompanyResponse.from(update(get(id), request));
    }

    /** Supprime l'entreprise et ses offres (et leurs candidatures) ; ses recruteurs sont détachés. */
    @Transactional
    public void delete(Long id) {
        Company company = get(id);
        company.getRecruiters().forEach(r -> r.setCompany(null));
        company.getRecruiters().clear();
        companyRepository.delete(company);
    }

    private Company update(Company company, CompanyRequest request) {
        checkNameAvailable(request.name(), company.getId());
        apply(company, request);
        return company;
    }

    private Company get(Long id) {
        return companyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Entreprise introuvable"));
    }

    private void checkNameAvailable(String name, Long currentId) {
        companyRepository.findByNameIgnoreCase(name.trim())
                .filter(existing -> !existing.getId().equals(currentId))
                .ifPresent(existing -> {
                    throw new ConflictException("Une entreprise porte déjà ce nom");
                });
    }

    private static void apply(Company company, CompanyRequest r) {
        company.setName(r.name().trim());
        company.setDescription(blankToNull(r.description()));
        company.setSector(blankToNull(r.sector()));
        company.setWebsite(blankToNull(r.website()));
        company.setLocation(blankToNull(r.location()));
    }
}
