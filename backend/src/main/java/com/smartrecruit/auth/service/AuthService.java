package com.smartrecruit.auth.service;

import com.smartrecruit.auth.dto.AuthResponse;
import com.smartrecruit.auth.dto.LoginRequest;
import com.smartrecruit.auth.dto.RegisterRequest;
import com.smartrecruit.candidate.entity.CandidateProfile;
import com.smartrecruit.candidate.repository.CandidateProfileRepository;
import com.smartrecruit.common.exception.BadRequestException;
import com.smartrecruit.common.exception.ConflictException;
import com.smartrecruit.common.exception.ResourceNotFoundException;
import com.smartrecruit.recruiter.entity.RecruiterProfile;
import com.smartrecruit.recruiter.repository.RecruiterProfileRepository;
import com.smartrecruit.security.AppUserDetails;
import com.smartrecruit.security.JwtService;
import com.smartrecruit.user.dto.UserResponse;
import com.smartrecruit.user.entity.Role;
import com.smartrecruit.user.entity.RoleName;
import com.smartrecruit.user.entity.User;
import com.smartrecruit.user.repository.RoleRepository;
import com.smartrecruit.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Set<RoleName> SELF_REGISTRABLE_ROLES = Set.of(RoleName.CANDIDAT, RoleName.RECRUTEUR);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final RecruiterProfileRepository recruiterProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    /** Crée le compte, le profil correspondant au rôle, et connecte directement l'utilisateur. */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (!SELF_REGISTRABLE_ROLES.contains(request.role())) {
            throw new BadRequestException("Rôle non autorisé à l'inscription");
        }
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Un compte existe déjà avec cet email");
        }
        Role role = roleRepository.findByName(request.role())
                .orElseThrow(() -> new IllegalStateException("Rôle absent de la base : " + request.role()));

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setRole(role);
        userRepository.save(user);

        if (role.getName() == RoleName.CANDIDAT) {
            CandidateProfile profile = new CandidateProfile();
            profile.setUser(user);
            candidateProfileRepository.save(profile);
        } else {
            RecruiterProfile profile = new RecruiterProfile();
            profile.setUser(user);
            recruiterProfileRepository.save(profile);
        }

        return tokenFor(user);
    }

    /** Lève BadCredentialsException (401) ou DisabledException (403), gérées par GlobalExceptionHandler. */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizeEmail(request.email()), request.password()));
        AppUserDetails principal = (AppUserDetails) authentication.getPrincipal();
        User user = userRepository.findById(principal.getId()).orElseThrow();
        return tokenFor(user);
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(Long userId) {
        return userRepository.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
    }

    private AuthResponse tokenFor(User user) {
        String token = jwtService.generateToken(new AppUserDetails(user));
        return AuthResponse.bearer(token, jwtService.getExpirationMs(), UserResponse.from(user));
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
