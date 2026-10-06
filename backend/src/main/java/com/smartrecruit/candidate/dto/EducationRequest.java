package com.smartrecruit.candidate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record EducationRequest(
        @NotBlank(message = "Le diplôme est obligatoire") @Size(max = 150) String degree,
        @Size(max = 150) String fieldOfStudy,
        @NotBlank(message = "L'établissement est obligatoire") @Size(max = 150) String institution,
        LocalDate startDate,
        LocalDate endDate,
        @Size(max = 2000) String description) {
}
