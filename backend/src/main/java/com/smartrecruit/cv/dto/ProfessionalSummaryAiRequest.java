package com.smartrecruit.cv.dto;

import java.util.List;

/**
 * Corps envoyé à FastAPI (POST /api/llm/professional-summary).
 * Uniquement des informations professionnelles anonymisées : jamais d'identité ni de PDF.
 */
public record ProfessionalSummaryAiRequest(
        List<String> skills,
        List<String> technologies,
        List<Education> educations,
        List<Experience> experiences,
        List<Language> languages,
        Integer totalExperienceMonths,
        String highestEducationLevel) {

    public record Education(String degree, String fieldOfStudy, String institution, String level) {
    }

    public record Experience(String jobTitle, String company, Integer durationMonths) {
    }

    public record Language(String name, String level, String cefr) {
    }
}
