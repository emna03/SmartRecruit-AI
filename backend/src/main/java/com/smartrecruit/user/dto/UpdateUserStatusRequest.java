package com.smartrecruit.user.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(
        @NotNull(message = "Le champ enabled est obligatoire")
        Boolean enabled) {
}
