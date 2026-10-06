package com.smartrecruit.joboffer.dto;

import com.smartrecruit.joboffer.entity.JobOfferStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateJobOfferStatusRequest(
        @NotNull(message = "Le statut est obligatoire (BROUILLON, PUBLIEE ou FERMEE)")
        JobOfferStatus status) {
}
