package com.smartrecruit.company.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CompanyRequest(
        @NotBlank(message = "Le nom de l'entreprise est obligatoire") @Size(max = 150) String name,
        @Size(max = 5000) String description,
        @Size(max = 100) String sector,
        @Size(max = 255)
        @Pattern(regexp = "^$|^https?://\\S+$", message = "Le site web doit commencer par http:// ou https://")
        String website,
        @Size(max = 150) String location) {
}
