package com.smartrecruit.application.dto;

import com.smartrecruit.application.entity.ApplicationStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateApplicationStatusRequest(
        @NotNull(message = "Le statut est obligatoire (EN_ATTENTE, PRESELECTIONNE, ACCEPTE ou REFUSE)")
        ApplicationStatus status) {
}
