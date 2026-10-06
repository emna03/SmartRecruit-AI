package com.smartrecruit.chatbot.dto;

/** Intentions reconnues par le chatbot du service IA (une valeur inconnue rend la réponse invalide). */
public enum ChatbotIntent {
    GREETING,
    THANKS,
    HELP,
    SEARCH_OFFERS,
    OFFER_SKILLS,
    OFFER_EXPERIENCE,
    OFFER_EDUCATION,
    OFFER_DETAILS,
    OFFER_SALARY,
    FAQ_APPLY,
    FAQ_CV,
    FAQ_MATCHING,
    FAQ_APPLICATIONS,
    FAQ_PROFILE,
    FAQ_PRIVACY,
    FAQ_RECOMMENDATION,
    UNKNOWN
}
