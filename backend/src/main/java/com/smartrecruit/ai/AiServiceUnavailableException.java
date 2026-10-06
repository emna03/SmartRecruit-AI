package com.smartrecruit.ai;

import lombok.Getter;

/**
 * Le service IA ne peut pas répondre (HTTP 503 pour le client).
 * Le message est destiné à l'utilisateur ; la cause technique précise est journalisée côté serveur.
 */
@Getter
public class AiServiceUnavailableException extends RuntimeException {

    public enum Reason { NOT_CONFIGURED, UNREACHABLE, TIMEOUT, AUTH_REJECTED, UPSTREAM_ERROR, INVALID_RESPONSE }

    static final String UNAVAILABLE_MESSAGE = "Le service IA est momentanément indisponible. Réessayez plus tard.";
    static final String TIMEOUT_MESSAGE = "Le service IA n'a pas répondu à temps. Réessayez dans quelques instants.";

    private final Reason reason;

    public AiServiceUnavailableException(Reason reason) {
        super(reason == Reason.TIMEOUT ? TIMEOUT_MESSAGE : UNAVAILABLE_MESSAGE);
        this.reason = reason;
    }
}
