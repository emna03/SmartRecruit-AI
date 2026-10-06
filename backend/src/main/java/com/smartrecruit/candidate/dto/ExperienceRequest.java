package com.smartrecruit.candidate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record ExperienceRequest(
        @NotBlank(message = "L'intitulé du poste est obligatoire") @Size(max = 150) String jobTitle,
        @NotBlank(message = "L'entreprise est obligatoire") @Size(max = 150) String companyName,
        @Size(max = 150) String location,
        @NotNull(message = "La date de début est obligatoire")
        @PastOrPresent(message = "La date de début ne peut pas être dans le futur") LocalDate startDate,
        LocalDate endDate,
        @Size(max = 2000) String description) {
}
