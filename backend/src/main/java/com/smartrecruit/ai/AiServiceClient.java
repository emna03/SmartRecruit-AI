package com.smartrecruit.ai;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Point d'accès unique au service IA FastAPI, partagé par les modules CV, matching, chatbot et ML.
 *
 * <ul>
 *   <li>chaque appel porte la clé interne (en-tête {@value #API_KEY_HEADER}) et un identifiant de corrélation ;</li>
 *   <li>service injoignable, délai dépassé, clé refusée, erreur 5xx ou réponse invalide :
 *       {@link AiServiceUnavailableException} (503) ;</li>
 *   <li>données refusées par le service IA (4xx) : {@link AiServiceRequestException} (422) ;</li>
 *   <li>toute réponse est validée (Bean Validation) avant d'être utilisée ou affichée.</li>
 * </ul>
 * Les journaux ne contiennent ni la clé ni le contenu échangé (données personnelles des CV).
 */
@Slf4j
@Component
public class AiServiceClient {

    public static final String API_KEY_HEADER = "X-Internal-Api-Key";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    static final String DEFAULT_REJECTION_MESSAGE = "Le service IA n'a pas pu traiter la demande.";
    private static final int MAX_MESSAGE_LENGTH = 300;

    private final RestClient restClient;
    private final Validator validator;
    private final boolean configured;

    public AiServiceClient(@Qualifier("aiRestClient") RestClient restClient, AiServiceProperties properties,
                           Validator validator) {
        this.restClient = restClient;
        this.validator = validator;
        this.configured = properties.isConfigured();
        if (!configured) {
            log.warn("AI_INTERNAL_API_KEY n'est pas définie : les fonctionnalités IA répondront 503.");
        }
    }

    public <T> T get(String path, Class<T> responseType) {
        return exchange(HttpMethod.GET, path, null, null, responseType);
    }

    public <T> T post(String path, Object body, Class<T> responseType) {
        return exchange(HttpMethod.POST, path, body, MediaType.APPLICATION_JSON, responseType);
    }

    /** Envoie un contenu binaire brut (ex. un CV PDF) ; le contenu n'est jamais journalisé. */
    public <T> T postBinary(String path, byte[] content, MediaType contentType, Class<T> responseType) {
        return exchange(HttpMethod.POST, path, content, contentType, responseType);
    }

    private <T> T exchange(HttpMethod method, String path, Object body, MediaType contentType, Class<T> responseType) {
        if (!configured) {
            log.error("Appel au service IA impossible ({} {}) : AI_INTERNAL_API_KEY absente", method, path);
            throw new AiServiceUnavailableException(AiServiceUnavailableException.Reason.NOT_CONFIGURED);
        }
        String requestId = UUID.randomUUID().toString().replace("-", "");
        T response;
        try {
            RestClient.RequestBodySpec request = restClient.method(method).uri(path)
                    .header(REQUEST_ID_HEADER, requestId)
                    .accept(MediaType.APPLICATION_JSON);
            if (body != null) {
                request.contentType(contentType).body(body);
            }
            response = request.retrieve().body(responseType);
        } catch (RestClientResponseException ex) {
            throw translate(ex, method, path, requestId);
        } catch (ResourceAccessException ex) {
            if (isResponseTimeout(ex)) {
                log.warn("Service IA : délai de réponse dépassé ({} {}) [{}]", method, path, requestId);
                throw new AiServiceUnavailableException(AiServiceUnavailableException.Reason.TIMEOUT);
            }
            log.warn("Service IA injoignable ({} {}) [{}] : {}", method, path, requestId, rootMessage(ex));
            throw new AiServiceUnavailableException(AiServiceUnavailableException.Reason.UNREACHABLE);
        } catch (RestClientException ex) {
            log.error("Service IA : réponse illisible ({} {}) [{}] : {}", method, path, requestId, rootMessage(ex));
            throw new AiServiceUnavailableException(AiServiceUnavailableException.Reason.INVALID_RESPONSE);
        }
        return validated(response, method, path, requestId);
    }

    private RuntimeException translate(RestClientResponseException ex, HttpMethod method, String path,
                                       String requestId) {
        int status = ex.getStatusCode().value();
        if (status == 401 || status == 403) {
            log.error("Le service IA a refusé la clé interne ({} {} -> {}) [{}] : AI_INTERNAL_API_KEY doit être "
                    + "identique pour Spring Boot et FastAPI", method, path, status, requestId);
            return new AiServiceUnavailableException(AiServiceUnavailableException.Reason.AUTH_REJECTED);
        }
        if (status >= 400 && status < 500) {
            log.info("Le service IA a refusé la demande ({} {} -> {}) [{}]", method, path, status, requestId);
            return new AiServiceRequestException(status, upstreamMessage(ex));
        }
        log.warn("Erreur du service IA ({} {} -> {}) [{}]", method, path, status, requestId);
        return new AiServiceUnavailableException(AiServiceUnavailableException.Reason.UPSTREAM_ERROR);
    }

    private <T> T validated(T response, HttpMethod method, String path, String requestId) {
        if (response == null) {
            log.error("Service IA : réponse vide ({} {}) [{}]", method, path, requestId);
            throw new AiServiceUnavailableException(AiServiceUnavailableException.Reason.INVALID_RESPONSE);
        }
        Set<ConstraintViolation<T>> violations = validator.validate(response);
        if (!violations.isEmpty()) {
            String fields = violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .sorted()
                    .collect(Collectors.joining(", "));
            log.error("Service IA : réponse rejetée ({} {}) [{}] : {}", method, path, requestId, fields);
            throw new AiServiceUnavailableException(AiServiceUnavailableException.Reason.INVALID_RESPONSE);
        }
        return response;
    }

    private static String upstreamMessage(RestClientResponseException ex) {
        try {
            UpstreamError error = ex.getResponseBodyAs(UpstreamError.class);
            if (error != null && error.message() != null && !error.message().isBlank()) {
                String message = error.message().strip();
                return message.length() > MAX_MESSAGE_LENGTH ? message.substring(0, MAX_MESSAGE_LENGTH) : message;
            }
        } catch (RuntimeException ignored) {
            // corps absent ou non JSON : message générique
        }
        return DEFAULT_REJECTION_MESSAGE;
    }

    private static boolean isResponseTimeout(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpConnectTimeoutException) {
                return false;
            }
            if (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }

    private static String rootMessage(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + (root.getMessage() == null ? "" : " " + root.getMessage());
    }

    /** Format d'erreur du service IA (identique à l'ApiError du backend) ; seul le message est repris. */
    record UpstreamError(String message) {
    }
}
