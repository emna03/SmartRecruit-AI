package com.smartrecruit.chatbot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Question du candidat.
 *
 * @param message    question en langage naturel (500 caractères maximum)
 * @param jobOfferId offre en cours de discussion (facultative) : celle que le candidat consulte ou a sélectionnée
 */
public record ChatbotQuestionRequest(
        @NotBlank(message = "La question ne peut pas être vide")
        @Size(max = 500, message = "La question ne doit pas dépasser 500 caractères")
        String message,
        @Positive Long jobOfferId) {
}
