package com.smartrecruit.common.model;

/**
 * Niveau d'étude, du plus bas au plus élevé.
 * L'ordre des constantes est utilisé pour comparer un candidat au niveau demandé par une offre :
 * ne pas le modifier sans mettre à jour le module de matching.
 */
public enum EducationLevel {
    BAC,
    BAC_PLUS_2,
    BAC_PLUS_3,
    BAC_PLUS_5,
    DOCTORAT;

    public int rank() {
        return ordinal();
    }
}
