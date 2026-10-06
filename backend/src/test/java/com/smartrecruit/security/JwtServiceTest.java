package com.smartrecruit.security;

import com.smartrecruit.user.entity.Role;
import com.smartrecruit.user.entity.RoleName;
import com.smartrecruit.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-key-0123456789-abcdefghijklmnop";

    private final JwtService jwtService = new JwtService(new JwtProperties(SECRET, 3_600_000));

    @Test
    void generatedTokenContainsUserId() {
        String token = jwtService.generateToken(user(42L));

        assertThat(jwtService.extractUserId(token)).contains(42L);
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService expiredService = new JwtService(new JwtProperties(SECRET, -1_000));
        String token = expiredService.generateToken(user(1L));

        assertThat(jwtService.extractUserId(token)).isEmpty();
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = jwtService.generateToken(user(1L));
        String[] parts = token.split("\\.");
        String forgedPayload = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"iss\":\"smartrecruit-ai\",\"sub\":\"999\"}".getBytes());
        String tampered = parts[0] + "." + forgedPayload + "." + parts[2];

        assertThat(jwtService.extractUserId(tampered)).isEmpty();
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        JwtService otherService = new JwtService(
                new JwtProperties("another-secret-key-0123456789-abcdefghijklmnop", 3_600_000));
        String token = otherService.generateToken(user(1L));

        assertThat(jwtService.extractUserId(token)).isEmpty();
    }

    @Test
    void garbageIsRejected() {
        assertThat(jwtService.extractUserId("not.a.jwt")).isEmpty();
        assertThat(jwtService.extractUserId("")).isEmpty();
    }

    private static AppUserDetails user(Long id) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setEmail("user@test.com");
        user.setPasswordHash("hash");
        user.setRole(new Role(RoleName.CANDIDAT));
        return new AppUserDetails(user);
    }
}
