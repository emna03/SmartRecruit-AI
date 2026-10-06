package com.smartrecruit.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ApplyRequest(
        @NotNull(message = "L'offre est obligatoire") Long jobOfferId,
        @Size(max = 5000, message = "La lettre de motivation fait 5000 caractères maximum") String coverLetter) {
}
