package com.smartrecruit.matching.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Score de compatibilité enregistré, tel qu'affiché au candidat et au recruteur.
 * {@code stale} : le CV a été modifié ou réanalysé depuis le calcul (le score doit être recalculé).
 */
public record MatchingResultResponse(
        double score,
        Double skillsScore,
        Double experienceScore,
        Double educationScore,
        List<MatchedSkill> matchedSkills,
        List<String> missingSkills,
        String explanation,
        String algorithmVersion,
        LocalDateTime computedAt,
        boolean stale,
        /** Similarité sémantique IA [0,1] ; null si non calculée. Indépendante du score métier. */
        Double semanticSimilarity,
        /** Score sémantique 0–100 ; null si non calculé. */
        Double semanticScore,
        String semanticModel) {
}
