package com.smartrecruit.application.dto;

import com.smartrecruit.application.entity.Application;
import com.smartrecruit.application.entity.ApplicationStatus;
import com.smartrecruit.candidate.dto.CandidateProfileResponse;

import java.time.LocalDateTime;

/** Détail d'une candidature pour le recruteur : profil complet du candidat. */
public record ApplicationDetailResponse(
        Long id,
        Long jobOfferId,
        String jobTitle,
        ApplicationStatus status,
        String coverLetter,
        LocalDateTime appliedAt,
        LocalDateTime updatedAt,
        CandidateProfileResponse candidate) {

    public static ApplicationDetailResponse from(Application a) {
        return new ApplicationDetailResponse(a.getId(), a.getJobOffer().getId(), a.getJobOffer().getTitle(),
                a.getStatus(), a.getCoverLetter(), a.getCreatedAt(), a.getUpdatedAt(),
                CandidateProfileResponse.from(a.getCandidate()));
    }
}
