package com.smartrecruit.user.service;

import com.smartrecruit.user.entity.Role;
import com.smartrecruit.user.entity.RoleName;
import com.smartrecruit.user.entity.User;
import com.smartrecruit.user.repository.RoleRepository;
import com.smartrecruit.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Crée le premier compte ADMIN à partir de ADMIN_EMAIL / ADMIN_PASSWORD si aucun admin n'existe.
 * Aucun mot de passe n'est écrit dans le code ni dans Git.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAccountInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email:}")
    private String adminEmail;

    @Value("${app.admin.password:}")
    private String adminPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminEmail.isBlank() || adminPassword.isBlank()) {
            return;
        }
        if (userRepository.countByRoleName(RoleName.ADMIN) > 0) {
            return;
        }
        if (adminPassword.length() < 8) {
            log.warn("ADMIN_PASSWORD trop court (8 caractères minimum) : compte admin non créé");
            return;
        }
        Role adminRole = roleRepository.findByName(RoleName.ADMIN).orElse(null);
        if (adminRole == null) {
            log.warn("Rôle ADMIN absent de la base : compte admin non créé");
            return;
        }
        User admin = new User();
        admin.setEmail(adminEmail.trim().toLowerCase(Locale.ROOT));
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setFirstName("Admin");
        admin.setLastName("SmartRecruit");
        admin.setRole(adminRole);
        userRepository.save(admin);
        log.info("Compte administrateur initial créé : {}", admin.getEmail());
    }
}
