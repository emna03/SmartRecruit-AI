package com.smartrecruit.skill.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SkillNamesRequest(
        @NotNull(message = "La liste des compétences est obligatoire")
        @Size(max = 50, message = "50 compétences maximum")
        List<@NotBlank(message = "Une compétence ne peut pas être vide")
             @Size(max = 100, message = "Une compétence fait 100 caractères maximum") String> skills) {
}
