package com.smartrecruit.cv.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Réponse LLM validée avant affichage (résumé professionnel + mots-clés). */
public record ProfessionalSummaryResponse(
        @NotBlank @Size(min = 40, max = 1200) String summary,
        @NotNull @Size(min = 1, max = 12) List<@NotBlank @Size(max = 100) String> keywords,
        @NotBlank @Size(max = 80) String model,
        @NotBlank @Size(max = 40) String provider,
        @NotBlank @Size(max = 20) String promptVersion,
        @NotBlank @Pattern(regexp = "llm") String source) {
}
