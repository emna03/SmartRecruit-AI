package com.smartrecruit.chatbot.dto;

import com.smartrecruit.common.model.EducationLevel;
import com.smartrecruit.joboffer.entity.ContractType;

import java.math.BigDecimal;
import java.util.List;

/**
 * Données envoyées au chatbot du service IA : la question et le catalogue des offres publiées.
 * Uniquement des informations publiques sur les offres ; rien sur le candidat (ni identité, ni CV, ni profil).
 */
public record AiChatbotRequest(String message, Long contextOfferId, List<CatalogOffer> offers) {

    public record CatalogOffer(
            Long id,
            String title,
            String company,
            String location,
            ContractType contractType,
            int minExperienceYears,
            EducationLevel educationLevel,
            BigDecimal salaryMin,
            BigDecimal salaryMax,
            List<String> requiredSkills) {
    }
}
