package com.smartrecruit.matching.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Réponse FastAPI POST /api/ml/semantic-matching (Sentence Transformer pré-entraîné).
 */
public record AiSemanticMatchingResult(
        @NotNull @DecimalMin("0") @DecimalMax("1") Double semanticSimilarity,
        @NotNull @Min(0) @Max(100) Integer semanticScore,
        @NotBlank @Size(max = 120) String model,
        @NotBlank @Size(max = 20) String version,
        @Size(max = 40) String provider) {
}
