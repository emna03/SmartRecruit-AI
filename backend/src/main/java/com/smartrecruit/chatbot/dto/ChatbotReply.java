package com.smartrecruit.chatbot.dto;

import java.util.List;

/**
 * Réponse du chatbot renvoyée à Angular.
 *
 * @param offers       offres citées (au plus 5 pour une recherche), dans l'ordre choisi par le chatbot
 * @param totalMatches nombre total d'offres correspondant à la recherche
 * @param contextOffer offre sur laquelle portera la question suivante (null si aucune)
 */
public record ChatbotReply(
        ChatbotIntent intent,
        String answer,
        double confidence,
        List<ChatbotOfferSummary> offers,
        int totalMatches,
        ChatbotOfferSummary contextOffer,
        AiChatbotResponse.Entities entities,
        List<String> suggestions,
        List<ChatbotAction> actions,
        String chatbotVersion) {
}
