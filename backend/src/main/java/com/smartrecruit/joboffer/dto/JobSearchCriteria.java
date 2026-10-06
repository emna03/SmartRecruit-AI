package com.smartrecruit.joboffer.dto;

import com.smartrecruit.common.model.EducationLevel;
import com.smartrecruit.joboffer.entity.ContractType;

/** Filtres de recherche des offres publiées ; tous optionnels. */
public record JobSearchCriteria(
        String keyword,
        String location,
        ContractType contractType,
        String skill,
        EducationLevel educationLevel,
        Integer maxExperienceYears,
        Long companyId) {
}
