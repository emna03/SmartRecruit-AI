package com.smartrecruit.matching.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Réponse du module matching (FastAPI), validée avant tout enregistrement ou affichage :
 * scores entre 0 et 100, listes bornées, compétences trouvées + manquantes = compétences requises.
 */
public record AiMatchingResult(
        @NotBlank @Size(max = 20) String algorithmVersion,
        @NotNull @DecimalMin("0") @DecimalMax("100") Double score,
        @NotNull @DecimalMin("0") @DecimalMax("100") Double skillsScore,
        @DecimalMin("0") @DecimalMax("100") Double experienceScore,
        @DecimalMin("0") @DecimalMax("100") Double educationScore,
        @NotNull @Min(1) @Max(50) Integer requiredSkillsCount,
        @NotNull @Size(max = 50) List<@NotNull @Valid MatchedSkill> matchedSkills,
        @NotNull @Size(max = 50) List<@NotBlank @Size(max = 100) String> missingSkills,
        @NotBlank @Size(max = 4000) String explanation) {

    @JsonIgnore
    @AssertTrue(message = "les compétences trouvées et manquantes doivent couvrir toutes les compétences requises")
    public boolean isSkillCountConsistent() {
        return matchedSkills == null || missingSkills == null || requiredSkillsCount == null
                || matchedSkills.size() + missingSkills.size() == requiredSkillsCount;
    }
}
