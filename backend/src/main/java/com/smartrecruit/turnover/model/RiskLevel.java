package com.smartrecruit.turnover.model;

/** Niveau de risque déduit du score et des seuils du modèle. */
public enum RiskLevel {
    FAIBLE, MOYEN, ELEVE;

    public static RiskLevel of(double probability, double mediumThreshold, double highThreshold) {
        if (probability >= highThreshold) {
            return ELEVE;
        }
        return probability >= mediumThreshold ? MOYEN : FAIBLE;
    }
}
