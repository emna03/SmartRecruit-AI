package com.smartrecruit.turnover.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.smartrecruit.turnover.model.RiskLevel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Prédiction du module ML (FastAPI), validée avant d'être renvoyée à Angular : score entre 0 et 1, niveau
 * cohérent avec le score et les seuils, au plus 5 facteurs portant sur des variables réellement envoyées,
 * sens cohérent avec le signe de l'impact, métriques de test entre 0 et 1. Rien n'est enregistré.
 */
public record TurnoverPrediction(
        @NotNull @DecimalMin("0") @DecimalMax("1") Double probability,
        @NotNull RiskLevel riskLevel,
        @NotNull @DecimalMin("0") @DecimalMax("1") Double mediumThreshold,
        @NotNull @DecimalMin("0") @DecimalMax("1") Double highThreshold,
        @NotNull @Size(max = 5) List<@NotNull @Valid Factor> factors,
        @NotNull @Valid ModelInfo model) {

    /** Noms des variables du modèle (champs de {@link TurnoverPredictionRequest}). */
    static final Set<String> FEATURES = Arrays.stream(TurnoverPredictionRequest.class.getRecordComponents())
            .map(component -> component.getName())
            .collect(Collectors.toUnmodifiableSet());

    @JsonIgnore
    @AssertTrue(message = "le niveau de risque doit correspondre au score et aux seuils")
    public boolean isRiskLevelConsistent() {
        if (probability == null || riskLevel == null || mediumThreshold == null || highThreshold == null) {
            return true;
        }
        return mediumThreshold < highThreshold
                && riskLevel == RiskLevel.of(probability, mediumThreshold, highThreshold);
    }

    @JsonIgnore
    @AssertTrue(message = "chaque facteur doit porter sur une variable distincte du modèle")
    public boolean isFactorsKnown() {
        if (factors == null) {
            return true;
        }
        List<String> names = factors.stream().filter(f -> f != null).map(Factor::feature).toList();
        return names.stream().allMatch(FEATURES::contains) && names.stream().distinct().count() == names.size();
    }

    public enum Direction { INCREASES_RISK, DECREASES_RISK }

    public enum ModelName { LOGISTIC_REGRESSION, RANDOM_FOREST }

    public enum ExplanationMethod { LINEAR, OCCLUSION }

    /** Variable ayant le plus influencé le score (comportement du modèle, pas une causalité). */
    public record Factor(
            @NotBlank @Size(max = 40) String feature,
            @NotBlank @Size(max = 80) String label,
            @NotNull Double impact,
            @NotNull Direction direction,
            @Size(min = 1, max = 40) String referenceValue) {

        @JsonIgnore
        @AssertTrue(message = "le sens doit correspondre au signe de l'impact")
        public boolean isDirectionConsistent() {
            return impact == null || direction == null
                    || (impact > 0 ? direction == Direction.INCREASES_RISK
                    : impact < 0 && direction == Direction.DECREASES_RISK);
        }
    }

    /** Modèle utilisé et ses métriques réellement mesurées sur le jeu de test. */
    public record ModelInfo(
            @NotNull ModelName name,
            @NotBlank @Size(max = 20) String version,
            @NotBlank @Size(max = 100) String dataset,
            @NotNull ExplanationMethod explanationMethod,
            @NotNull @DecimalMin("0") @DecimalMax("1") Double testAccuracy,
            @NotNull @DecimalMin("0") @DecimalMax("1") Double testPrecision,
            @NotNull @DecimalMin("0") @DecimalMax("1") Double testRecall,
            @NotNull @DecimalMin("0") @DecimalMax("1") Double testF1,
            @NotNull @DecimalMin("0") @DecimalMax("1") Double testRocAuc) {
    }
}
