package com.smartrecruit.matching.dto;

import com.smartrecruit.common.model.EducationLevel;

import java.util.List;

/**
 * Données envoyées au service IA (POST /api/matching/score) : uniquement ce qui sert au calcul.
 * Ni nom, ni email, ni texte du CV ; les compétences, la durée d'expérience et le niveau viennent de l'analyse du CV.
 */
public record MatchingRequest(Candidate candidate, Offer offer) {

    public record Candidate(List<String> skills, Integer experienceMonths, EducationLevel educationLevel) {
    }

    public record Offer(List<String> requiredSkills, int minExperienceYears, EducationLevel educationLevel) {
    }
}
