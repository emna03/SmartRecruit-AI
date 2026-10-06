package com.smartrecruit.matching.dto;

/**
 * Compatibilité du candidat connecté avec une offre.
 * <ul>
 *   <li>{@code CALCULE} : score calculé à l'instant et enregistré ;</li>
 *   <li>{@code CV_ABSENT} / {@code CV_NON_ANALYSE} : aucun score possible, {@code message} explique quoi faire ;</li>
 *   <li>{@code IA_INDISPONIBLE} : service IA arrêté, trop lent ou réponse invalide ; {@code result} contient le
 *       dernier score enregistré s'il existe (marqué {@code stale}).</li>
 * </ul>
 */
public record CandidateMatchingResponse(Status status, String message, MatchingResultResponse result) {

    public enum Status {
        CALCULE,
        CV_ABSENT,
        CV_NON_ANALYSE,
        IA_INDISPONIBLE
    }
}
