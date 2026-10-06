package com.smartrecruit.joboffer.dto;

import com.smartrecruit.common.model.EducationLevel;
import com.smartrecruit.joboffer.entity.ContractType;
import com.smartrecruit.joboffer.entity.JobOfferStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/** Création / modification d'une offre. Si status est absent, l'offre est créée en BROUILLON. */
public record JobOfferRequest(
        @NotBlank(message = "Le titre est obligatoire") @Size(max = 150) String title,
        @NotBlank(message = "La description est obligatoire") @Size(max = 10000) String description,
        @Size(max = 150) String location,
        @NotNull(message = "Le type de contrat est obligatoire") ContractType contractType,
        @NotNull(message = "L'expérience minimale est obligatoire")
        @Min(value = 0, message = "L'expérience minimale ne peut pas être négative")
        @Max(value = 50, message = "50 ans d'expérience maximum") Integer minExperienceYears,
        EducationLevel educationLevel,
        @DecimalMin(value = "0", message = "Le salaire ne peut pas être négatif") @Digits(integer = 8, fraction = 2)
        BigDecimal salaryMin,
        @DecimalMin(value = "0", message = "Le salaire ne peut pas être négatif") @Digits(integer = 8, fraction = 2)
        BigDecimal salaryMax,
        @NotEmpty(message = "Indiquez au moins une compétence requise")
        @Size(max = 30, message = "30 compétences maximum")
        List<@NotBlank @Size(max = 100) String> requiredSkills,
        JobOfferStatus status) {
}
