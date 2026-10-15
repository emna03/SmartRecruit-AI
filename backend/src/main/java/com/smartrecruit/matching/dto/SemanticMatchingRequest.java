package com.smartrecruit.matching.dto;

import java.util.List;

/**
 * Corps envoyé à FastAPI (POST /api/ml/semantic-matching) : données professionnelles uniquement.
 */
public record SemanticMatchingRequest(Candidate candidate, Offer offer) {

    public record Candidate(
            List<String> skills,
            List<String> technologies,
            List<Education> educations,
            List<Experience> experiences,
            List<Language> languages,
            Integer totalExperienceMonths,
            String highestEducationLevel) {
    }

    public record Education(String degree, String fieldOfStudy, String institution, String level) {
    }

    public record Experience(String jobTitle, String company, Integer durationMonths, String description) {
    }

    public record Language(String name, String level, String cefr) {
    }

    public record Offer(
            String title,
            List<String> requiredSkills,
            String description,
            int minExperienceYears,
            String educationLevel) {
    }
}
