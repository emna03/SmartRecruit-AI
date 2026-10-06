package com.smartrecruit.candidate.dto;

import com.smartrecruit.candidate.entity.Experience;

import java.time.LocalDate;

public record ExperienceResponse(
        Long id,
        String jobTitle,
        String companyName,
        String location,
        LocalDate startDate,
        LocalDate endDate,
        boolean current,
        String description) {

    public static ExperienceResponse from(Experience e) {
        return new ExperienceResponse(e.getId(), e.getJobTitle(), e.getCompanyName(), e.getLocation(),
                e.getStartDate(), e.getEndDate(), e.getEndDate() == null, e.getDescription());
    }
}
