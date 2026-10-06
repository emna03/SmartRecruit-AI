package com.smartrecruit.ai;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Contrôles appliqués au démarrage sur AI_SERVICE_URL, AI_INTERNAL_API_KEY et AI_TIMEOUT_SECONDS. */
class AiServicePropertiesTest {

    private static final String URL = "http://localhost:8000";
    private static final String KEY = "test-only-internal-key-0123456789";

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static Set<ConstraintViolation<AiServiceProperties>> validate(String url, String key, int timeout) {
        return validator.validate(new AiServiceProperties(url, key, timeout));
    }

    @Test
    void validConfiguration() {
        assertThat(validate(URL, KEY, 20)).isEmpty();
        assertThat(new AiServiceProperties(URL, KEY, 20).isConfigured()).isTrue();
    }

    @Test
    void emptyKeyIsAllowedButDisablesAi() {
        assertThat(validate(URL, "", 20)).isEmpty();
        assertThat(new AiServiceProperties(URL, "", 20).isConfigured()).isFalse();
        assertThat(new AiServiceProperties(URL, null, 20).isConfigured()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"short-key", "change_me_internal_key", "key with spaces 0123456789"})
    void weakOrExampleKeyIsRejected(String key) {
        assertThat(validate(URL, key, 20)).extracting(v -> v.getPropertyPath().toString()).containsExactly("apiKey");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "localhost:8000", "ftp://ai-service"})
    void invalidUrlIsRejected(String url) {
        assertThat(validate(url, KEY, 20)).extracting(v -> v.getPropertyPath().toString()).contains("url");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 121})
    void timeoutMustStayReasonable(int timeout) {
        assertThat(validate(URL, KEY, timeout))
                .extracting(v -> v.getPropertyPath().toString()).containsExactly("timeoutSeconds");
    }
}
