package com.smartrecruit.support;

import com.jayway.jsonpath.JsonPath;
import com.smartrecruit.user.entity.Role;
import com.smartrecruit.user.entity.RoleName;
import com.smartrecruit.user.entity.User;
import com.smartrecruit.user.repository.RoleRepository;
import com.smartrecruit.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Base des tests d'API : base H2, transaction annulée après chaque test, utilitaires de requêtes. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public abstract class ApiTestSupport {

    protected static final String PASSWORD = "Passw0rd!";

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected RoleRepository roleRepository;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @BeforeEach
    void seedRoles() {
        Arrays.stream(RoleName.values())
                .filter(name -> roleRepository.findByName(name).isEmpty())
                .forEach(name -> roleRepository.save(new Role(name)));
    }

    protected String registerCandidate(String email) throws Exception {
        return register(email, "CANDIDAT");
    }

    protected String registerRecruiter(String email) throws Exception {
        return register(email, "RECRUTEUR");
    }

    /** Recruteur inscrit avec son entreprise déjà créée. */
    protected String recruiterWithCompany(String email, String companyName) throws Exception {
        String token = registerRecruiter(email);
        postJson("/api/recruiter/company", token, """
                {"name":"%s","sector":"IT","location":"Tunis"}
                """.formatted(companyName)).andExpect(status().isCreated());
        return token;
    }

    protected String adminToken() throws Exception {
        User admin = new User();
        admin.setEmail("admin@test.com");
        admin.setPasswordHash(passwordEncoder.encode(PASSWORD));
        admin.setFirstName("Admin");
        admin.setLastName("Test");
        admin.setRole(roleRepository.findByName(RoleName.ADMIN).orElseThrow());
        userRepository.save(admin);
        String body = """
                {"email":"admin@test.com","password":"%s"}
                """.formatted(PASSWORD);
        return read(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body)),
                "$.accessToken");
    }

    /** Crée une offre et renvoie son id. */
    protected long createOffer(String token, String title, String status, String... skills) throws Exception {
        String skillsJson = String.join(",", Arrays.stream(skills).map(s -> "\"" + s + "\"").toList());
        String body = """
                {"title":"%s","description":"Poste de %s","location":"Tunis","contractType":"CDI",
                 "minExperienceYears":2,"educationLevel":"BAC_PLUS_5","requiredSkills":[%s],"status":"%s"}
                """.formatted(title, title, skillsJson, status);
        return ((Number) JsonPath.read(postJson("/api/recruiter/jobs", token, body)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    protected ResultActions getAs(String path, String token) throws Exception {
        return mvc.perform(auth(get(path), token));
    }

    protected ResultActions postJson(String path, String token, String json) throws Exception {
        return mvc.perform(auth(post(path), token).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    protected ResultActions putJson(String path, String token, String json) throws Exception {
        return mvc.perform(auth(put(path), token).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    protected ResultActions patchJson(String path, String token, String json) throws Exception {
        return mvc.perform(auth(patch(path), token).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    protected ResultActions deleteAs(String path, String token) throws Exception {
        return mvc.perform(auth(delete(path), token));
    }

    protected static <T> T read(ResultActions result, String jsonPath) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), jsonPath);
    }

    protected static long readId(ResultActions result, String jsonPath) throws Exception {
        return ((Number) read(result, jsonPath)).longValue();
    }

    private String register(String email, String role) throws Exception {
        String body = """
                {"email":"%s","password":"%s","firstName":"Test","lastName":"User","role":"%s"}
                """.formatted(email, PASSWORD, role);
        return read(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()), "$.accessToken");
    }

    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder builder, String token) {
        return token == null ? builder : builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
}
