package com.smartrecruit.chatbot.dto;

import com.smartrecruit.common.model.EducationLevel;
import com.smartrecruit.joboffer.entity.ContractType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Réponse du chatbot du service IA, validée avant tout affichage. Les offres sont désignées par leur
 * identifiant ; {@code ChatbotService} vérifie en plus qu'elles font partie du catalogue envoyé.
 */
public record AiChatbotResponse(
        @NotBlank @Size(max = 20) String chatbotVersion,
        @NotNull ChatbotIntent intent,
        @NotNull @DecimalMin("0") @DecimalMax("1") Double confidence,
        @NotBlank @Size(max = 2000) String answer,
        @NotNull @Size(max = 10) List<@NotNull @Positive Long> offerIds,
        @NotNull @PositiveOrZero Integer totalMatches,
        @Positive Long contextOfferId,
        @NotNull @Valid Entities entities,
        @NotNull @Size(max = 4) List<@NotBlank @Size(max = 120) String> suggestions,
        @NotNull @Size(max = 3) List<@NotNull ChatbotAction> actions) {

    /** Informations extraites de la question (affichées pour expliquer la réponse). */
    public record Entities(
            @NotNull @Size(max = 20) List<@NotBlank @Size(max = 100) String> skills,
            @Min(0) @Max(50) Integer experienceYears,
            EducationLevel educationLevel,
            ContractType contractType,
            @Size(max = 150) String location,
            @NotNull @Size(max = 5) List<@NotBlank @Size(max = 40) String> keywords) {
    }
}
