package com.smartrecruit.recruiter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateRecruiterProfileRequest(
        @NotBlank(message = "Le prénom est obligatoire") @Size(max = 100) String firstName,
        @NotBlank(message = "Le nom est obligatoire") @Size(max = 100) String lastName,
        @Size(max = 100) String jobTitle,
        @Pattern(regexp = "^$|^[+0-9 ().-]{6,30}$", message = "Numéro de téléphone invalide") String phone) {
}
