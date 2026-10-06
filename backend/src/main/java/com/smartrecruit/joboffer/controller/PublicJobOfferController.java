package com.smartrecruit.joboffer.controller;

import com.smartrecruit.common.dto.PageResponse;
import com.smartrecruit.common.model.EducationLevel;
import com.smartrecruit.joboffer.dto.JobOfferResponse;
import com.smartrecruit.joboffer.dto.JobSearchCriteria;
import com.smartrecruit.joboffer.entity.ContractType;
import com.smartrecruit.joboffer.service.JobOfferService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Consultation publique des offres publiées (sans authentification). */
@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class PublicJobOfferController {

    private final JobOfferService jobOfferService;

    @GetMapping
    public PageResponse<JobOfferResponse> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) ContractType contractType,
            @RequestParam(required = false) String skill,
            @RequestParam(required = false) EducationLevel educationLevel,
            @RequestParam(required = false) Integer maxExperienceYears,
            @RequestParam(required = false) Long companyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        JobSearchCriteria criteria = new JobSearchCriteria(keyword, location, contractType, skill,
                educationLevel, maxExperienceYears, companyId);
        return jobOfferService.searchPublished(criteria, page, size);
    }

    @GetMapping("/{id}")
    public JobOfferResponse get(@PathVariable Long id) {
        return jobOfferService.getPublished(id);
    }
}
