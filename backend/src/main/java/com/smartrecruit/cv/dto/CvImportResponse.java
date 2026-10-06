package com.smartrecruit.cv.dto;

import com.smartrecruit.candidate.dto.CandidateProfileResponse;

/** Bilan de l'import dans le profil : éléments ajoutés, éléments ignorés (doublons ou incomplets). */
public record CvImportResponse(
        int skillsAdded,
        int educationsAdded,
        int experiencesAdded,
        int skipped,
        boolean educationLevelSet,
        boolean yearsOfExperienceSet,
        CandidateProfileResponse profile) {
}
