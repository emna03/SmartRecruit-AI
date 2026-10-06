package com.smartrecruit.matching.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Compétence requise trouvée : {@code name} (offre), {@code matchedBy} (compétence du CV qui la couvre),
 * type de correspondance et crédit accordé (1 = exacte ou impliquée, 0,5 = technologie proche).
 */
public record MatchedSkill(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 100) String matchedBy,
        @NotNull MatchType matchType,
        @DecimalMin(value = "0", inclusive = false) @DecimalMax("1") double credit) {

    public enum MatchType {
        EXACT,
        IMPLIED,
        RELATED
    }
}
