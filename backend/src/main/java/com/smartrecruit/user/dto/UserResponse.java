package com.smartrecruit.user.dto;

import com.smartrecruit.user.entity.RoleName;
import com.smartrecruit.user.entity.User;

import java.time.LocalDateTime;

/** Vue publique d'un utilisateur : ne contient jamais le hash du mot de passe. */
public record UserResponse(
        Long id,
        String email,
        String firstName,
        String lastName,
        RoleName role,
        boolean enabled,
        LocalDateTime createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(),
                user.getRole().getName(), user.isEnabled(), user.getCreatedAt());
    }
}
