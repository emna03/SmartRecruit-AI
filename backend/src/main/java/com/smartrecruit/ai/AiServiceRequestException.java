package com.smartrecruit.ai;

import lombok.Getter;

/** Le service IA a refusé de traiter des données (erreur 4xx hors authentification) : HTTP 422 pour le client. */
@Getter
public class AiServiceRequestException extends RuntimeException {

    private final int upstreamStatus;

    public AiServiceRequestException(int upstreamStatus, String message) {
        super(message);
        this.upstreamStatus = upstreamStatus;
    }
}
