package com.smartrecruit.cv.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.smartrecruit.common.model.EducationLevel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Résultat du CV Parser (FastAPI, POST /api/cv/parse), validé ici avant tout enregistrement ou affichage.
 * Stocké en JSON dans cvs.extracted_data, sans le texte du CV (conservé à part, coordonnées masquées).
 */
public record CvParseResult(
        @NotBlank @Size(max = 20) String parserVersion,
        @NotBlank @Size(max = 1000) String method,
        @NotNull @Pattern(regexp = "fr|en|inconnu") String documentLanguage,
        @Min(1) @Max(50) int pageCount,
        @Min(0) int textLength,
        @NotNull @Size(max = 20) List<@NotBlank @Size(max = 30) String> sectionsDetected,
        @NotNull @Size(max = 200) List<@NotNull @Valid DetectedSkill> skills,
        @NotNull @Size(max = 200) List<@NotNull @Valid DetectedSkill> technologies,
        @NotNull @Size(max = 20) List<@NotNull @Valid EducationEntry> educations,
        @NotNull @Size(max = 30) List<@NotNull @Valid ExperienceEntry> experiences,
        @NotNull @Size(max = 15) List<@NotNull @Valid LanguageEntry> languages,
        @Min(0) @Max(720) Integer totalExperienceMonths,
        EducationLevel highestEducationLevel,
        @NotNull @Size(max = 20) List<@NotBlank @Size(max = 300) String> warnings,
        @JsonInclude(JsonInclude.Include.NON_NULL) @NotNull @Size(max = 100_000) String text,
        @Min(0) long processingTimeMs) {

    /** « 2023 » ou « 2023-03 ». */
    public static final String PARTIAL_DATE = "(19|20)\\d{2}(-(0[1-9]|1[0-2]))?";

    public CvParseResult withoutText() {
        return new CvParseResult(parserVersion, method, documentLanguage, pageCount, textLength, sectionsDetected,
                skills, technologies, educations, experiences, languages, totalExperienceMonths,
                highestEducationLevel, warnings, null, processingTimeMs);
    }

    public record DetectedSkill(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 50) String category,
            @Min(1) int occurrences) {
    }

    public record EducationEntry(
            @NotBlank @Size(max = 150) String degree,
            @Size(max = 150) String fieldOfStudy,
            @Size(max = 150) String institution,
            @Pattern(regexp = PARTIAL_DATE) String startDate,
            @Pattern(regexp = PARTIAL_DATE) String endDate,
            EducationLevel level) {
    }

    public record ExperienceEntry(
            @NotBlank @Size(max = 150) String jobTitle,
            @Size(max = 150) String company,
            @Size(max = 150) String location,
            @Pattern(regexp = PARTIAL_DATE) String startDate,
            @Pattern(regexp = PARTIAL_DATE) String endDate,
            boolean current,
            @Min(0) @Max(720) Integer durationMonths,
            @Size(max = 1500) String description) {
    }

    public record LanguageEntry(
            @NotBlank @Size(max = 50) String name,
            @Pattern(regexp = "NATIF|BILINGUE|COURANT|PROFESSIONNEL|INTERMEDIAIRE|DEBUTANT") String level,
            @Pattern(regexp = "[ABC][12]") String cefr,
            @Size(max = 30) String certification) {
    }
}
