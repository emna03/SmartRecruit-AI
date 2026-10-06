package com.smartrecruit.candidate.dto;

import com.smartrecruit.common.model.EducationLevel;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateCandidateProfileRequest(
        @NotBlank(message = "Le prénom est obligatoire") @Size(max = 100) String firstName,
        @NotBlank(message = "Le nom est obligatoire") @Size(max = 100) String lastName,
        @Size(max = 150) String headline,
        @Size(max = 5000) String summary,
        @Pattern(regexp = "^$|^[+0-9 ().-]{6,30}$", message = "Numéro de téléphone invalide") String phone,
        @Size(max = 150) String location,
        @Min(value = 0, message = "Les années d'expérience ne peuvent pas être négatives")
        @Max(value = 60, message = "60 années d'expérience maximum") Integer yearsOfExperience,
        EducationLevel educationLevel) {
}
