package com.smartrecruit.chatbot.dto;

import com.smartrecruit.common.model.EducationLevel;
import com.smartrecruit.joboffer.entity.ContractType;

import java.util.List;

/** Offre citée dans une réponse, construite à partir de la base (jamais à partir du texte du service IA). */
public record ChatbotOfferSummary(
        Long id,
        String title,
        String companyName,
        String location,
        ContractType contractType,
        int minExperienceYears,
        EducationLevel educationLevel,
        List<String> requiredSkills) {
}
