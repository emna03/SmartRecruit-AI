package com.smartrecruit.clustering.dto;

import java.util.List;

public record AiClusteringTrainRequest(List<Profile> profiles) {

    public record Profile(
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
}
