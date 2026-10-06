package com.smartrecruit.cv.dto;

import com.smartrecruit.cv.entity.CvParsingStatus;

import java.time.LocalDateTime;

/**
 * CV du candidat connecté : fichier, état de l'analyse et informations extraites.
 * {@code analysis} est null tant que l'analyse n'a pas abouti (statut EN_COURS ou ECHEC).
 */
public record CvResponse(
        Long id,
        String originalFileName,
        long fileSize,
        CvParsingStatus parsingStatus,
        String parsingError,
        LocalDateTime parsedAt,
        LocalDateTime updatedAt,
        CvParseResult analysis) {
}
