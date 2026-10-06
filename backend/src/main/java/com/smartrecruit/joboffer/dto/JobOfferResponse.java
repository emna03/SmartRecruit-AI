package com.smartrecruit.joboffer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.smartrecruit.common.model.EducationLevel;
import com.smartrecruit.company.dto.CompanySummary;
import com.smartrecruit.joboffer.entity.ContractType;
import com.smartrecruit.joboffer.entity.JobOffer;
import com.smartrecruit.joboffer.entity.JobOfferStatus;
import com.smartrecruit.skill.service.SkillService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** applicationCount n'est renseigné que pour le recruteur et l'admin (absent de la vue publique). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JobOfferResponse(
        Long id,
        String title,
        String description,
        String location,
        ContractType contractType,
        int minExperienceYears,
        EducationLevel educationLevel,
        BigDecimal salaryMin,
        BigDecimal salaryMax,
        JobOfferStatus status,
        List<String> requiredSkills,
        CompanySummary company,
        Integer applicationCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static JobOfferResponse forPublic(JobOffer o) {
        return of(o, null);
    }

    public static JobOfferResponse forOwner(JobOffer o) {
        return of(o, o.getApplications().size());
    }

    private static JobOfferResponse of(JobOffer o, Integer applicationCount) {
        return new JobOfferResponse(o.getId(), o.getTitle(), o.getDescription(), o.getLocation(), o.getContractType(),
                o.getMinExperienceYears(), o.getEducationLevel(), o.getSalaryMin(), o.getSalaryMax(), o.getStatus(),
                SkillService.names(o.getRequiredSkills()), CompanySummary.from(o.getCompany()), applicationCount,
                o.getCreatedAt(), o.getUpdatedAt());
    }
}
