package com.smartrecruit.candidate.dto;

import com.smartrecruit.candidate.entity.Education;

import java.time.LocalDate;

public record EducationResponse(
        Long id,
        String degree,
        String fieldOfStudy,
        String institution,
        LocalDate startDate,
        LocalDate endDate,
        String description) {

    public static EducationResponse from(Education e) {
        return new EducationResponse(e.getId(), e.getDegree(), e.getFieldOfStudy(), e.getInstitution(),
                e.getStartDate(), e.getEndDate(), e.getDescription());
    }
}
