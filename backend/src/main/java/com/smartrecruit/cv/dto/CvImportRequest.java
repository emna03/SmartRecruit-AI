package com.smartrecruit.cv.dto;

/** Éléments du CV analysé à ajouter au profil (action explicite du candidat) ; un champ absent vaut false. */
public record CvImportRequest(Boolean skills, Boolean educations, Boolean experiences) {

    public boolean includeSkills() {
        return Boolean.TRUE.equals(skills);
    }

    public boolean includeEducations() {
        return Boolean.TRUE.equals(educations);
    }

    public boolean includeExperiences() {
        return Boolean.TRUE.equals(experiences);
    }
}
