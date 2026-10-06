package com.smartrecruit.auth;

import com.jayway.jsonpath.JsonPath;
import com.smartrecruit.candidate.repository.CandidateProfileRepository;
import com.smartrecruit.recruiter.repository.RecruiterProfileRepository;
import com.smartrecruit.user.entity.Role;
import com.smartrecruit.user.entity.RoleName;
import com.smartrecruit.user.entity.User;
import com.smartrecruit.user.repository.RoleRepository;
import com.smartrecruit.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthApiTest {

    private static final String PASSWORD = "Passw0rd!";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CandidateProfileRepository candidateProfileRepository;

    @Autowired
    private RecruiterProfileRepository recruiterProfileRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void seedRoles() {
        Arrays.stream(RoleName.values())
                .filter(name -> roleRepository.findByName(name).isEmpty())
                .forEach(name -> roleRepository.save(new Role(name)));
    }

    // ---------- Inscription ----------

    @Test
    void registerCandidateReturnsTokenAndCreatesProfile() throws Exception {
        register("alice@test.com", PASSWORD, "CANDIDAT")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value("alice@test.com"))
                .andExpect(jsonPath("$.user.role").value("CANDIDAT"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());

        User user = userRepository.findByEmailIgnoreCase("alice@test.com").orElseThrow();
        assertThat(candidateProfileRepository.findByUserId(user.getId())).isPresent();
        assertThat(user.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, user.getPasswordHash())).isTrue();
    }

    @Test
    void registerRecruiterCreatesRecruiterProfile() throws Exception {
        register("bob@acme.com", PASSWORD, "RECRUTEUR").andExpect(status().isCreated());

        User user = userRepository.findByEmailIgnoreCase("bob@acme.com").orElseThrow();
        assertThat(recruiterProfileRepository.findByUserId(user.getId())).isPresent();
        assertThat(candidateProfileRepository.findByUserId(user.getId())).isEmpty();
    }

    @Test
    void registerAsAdminIsRefused() throws Exception {
        register("hacker@test.com", PASSWORD, "ADMIN")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Rôle non autorisé à l'inscription"));
        assertThat(userRepository.existsByEmailIgnoreCase("hacker@test.com")).isFalse();
    }

    @Test
    void registerWithExistingEmailReturnsConflict() throws Exception {
        register("dup@test.com", PASSWORD, "CANDIDAT").andExpect(status().isCreated());

        register("DUP@test.com", PASSWORD, "CANDIDAT").andExpect(status().isConflict());
    }

    @Test
    void registerWithInvalidDataReturnsFieldErrors() throws Exception {
        register("not-an-email", "short", "CANDIDAT")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    // ---------- Connexion ----------

    @Test
    void loginWithValidCredentialsReturnsToken() throws Exception {
        register("carl@test.com", PASSWORD, "CANDIDAT");

        login("Carl@Test.com", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.role").value("CANDIDAT"));
    }

    @Test
    void loginWithWrongPasswordOrUnknownEmailReturnsSame401() throws Exception {
        register("dina@test.com", PASSWORD, "CANDIDAT");

        login("dina@test.com", "WrongPass1")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Email ou mot de passe incorrect"));
        login("nobody@test.com", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Email ou mot de passe incorrect"));
    }

    // ---------- Jeton ----------

    @Test
    void meRequiresValidToken() throws Exception {
        String token = tokenOf(register("eve@test.com", PASSWORD, "CANDIDAT"));

        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentification requise"));
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token + "x"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("eve@test.com"));
    }

    // ---------- Rôles ----------

    @Test
    void eachRoleIsRestrictedToItsOwnArea() throws Exception {
        String candidate = tokenOf(register("cand@test.com", PASSWORD, "CANDIDAT"));
        String recruiter = tokenOf(register("recr@test.com", PASSWORD, "RECRUTEUR"));

        mvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + candidate))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Accès refusé"));
        mvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + recruiter))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/recruiter/jobs").header(HttpHeaders.AUTHORIZATION, "Bearer " + candidate))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/candidate/applications").header(HttpHeaders.AUTHORIZATION, "Bearer " + recruiter))
                .andExpect(status().isForbidden());
    }

    @Test
    void publicJobEndpointsDoNotRequireToken() throws Exception {
        // Aucun contrôleur d'offres à cette étape : 404 prouve que la sécurité laisse passer (sinon 401).
        mvc.perform(get("/api/jobs")).andExpect(status().isNotFound());
    }

    // ---------- Administration des utilisateurs ----------

    @Test
    void adminCanListAndFilterUsers() throws Exception {
        String admin = adminToken();
        register("c1@test.com", PASSWORD, "CANDIDAT");
        register("r1@test.com", PASSWORD, "RECRUTEUR");

        mvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
        mvc.perform(get("/api/admin/users").param("role", "CANDIDAT")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].role", everyItem(is("CANDIDAT"))));
    }

    @Test
    void disabledUserCannotLoginAndHisTokenStopsWorking() throws Exception {
        String admin = adminToken();
        String userToken = tokenOf(register("frank@test.com", PASSWORD, "CANDIDAT"));
        Long userId = userRepository.findByEmailIgnoreCase("frank@test.com").orElseThrow().getId();

        mvc.perform(patch("/api/admin/users/{id}/status", userId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        login("frank@test.com", PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Compte désactivé"));
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCannotDisableOrDeleteHimself() throws Exception {
        String admin = adminToken();
        Long adminId = userRepository.findByEmailIgnoreCase("admin@test.com").orElseThrow().getId();

        mvc.perform(patch("/api/admin/users/{id}/status", adminId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\": false}"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/admin/users/{id}", adminId).header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminCanDeleteUser() throws Exception {
        String admin = adminToken();
        register("gina@test.com", PASSWORD, "CANDIDAT");
        Long userId = userRepository.findByEmailIgnoreCase("gina@test.com").orElseThrow().getId();

        mvc.perform(delete("/api/admin/users/{id}", userId).header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isNoContent());
        assertThat(userRepository.findById(userId)).isEmpty();
        assertThat(candidateProfileRepository.findByUserId(userId)).isEmpty();

        mvc.perform(get("/api/admin/users/{id}", userId).header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isNotFound());
    }

    // ---------- CORS ----------

    @Test
    void corsPreflightFromAngularIsAllowed() throws Exception {
        mvc.perform(options("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200"));

        mvc.perform(options("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, "http://evil.example.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden());
    }

    // ---------- Utilitaires ----------

    private ResultActions register(String email, String password, String role) throws Exception {
        String body = """
                {"email":"%s","password":"%s","firstName":"Test","lastName":"User","role":"%s"}
                """.formatted(email, password, role);
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions login(String email, String password) throws Exception {
        String body = """
                {"email":"%s","password":"%s"}
                """.formatted(email, password);
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String tokenOf(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.accessToken");
    }

    private String adminToken() throws Exception {
        User admin = new User();
        admin.setEmail("admin@test.com");
        admin.setPasswordHash(passwordEncoder.encode(PASSWORD));
        admin.setFirstName("Admin");
        admin.setLastName("Test");
        admin.setRole(roleRepository.findByName(RoleName.ADMIN).orElseThrow());
        userRepository.save(admin);
        return tokenOf(login("admin@test.com", PASSWORD));
    }
}
