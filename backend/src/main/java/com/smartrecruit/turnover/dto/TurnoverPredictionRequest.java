package com.smartrecruit.turnover.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.smartrecruit.common.json.StrictBool;
import com.smartrecruit.common.json.StrictInt;
import com.smartrecruit.turnover.model.BusinessTravel;
import com.smartrecruit.turnover.model.Department;
import com.smartrecruit.turnover.model.EducationField;
import com.smartrecruit.turnover.model.JobRole;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Les 24 variables utilisées par le modèle de turnover, et rien d'autre : ni nom, ni âge, ni genre, ni situation
 * familiale (un champ inconnu envoyé par le client est ignoré et n'est jamais transmis au service IA).
 * Bornes = valeurs observées dans le dataset d'entraînement ; mêmes règles que le service IA.
 */
public record TurnoverPredictionRequest(
        @NotNull BusinessTravel businessTravel,
        @NotNull Department department,
        @NotNull EducationField educationField,
        @NotNull JobRole jobRole,
        @NotNull @StrictBool Boolean overTime,
        @NotNull @StrictInt @Min(1) @Max(29) Integer distanceFromHome,
        @NotNull @StrictInt @Min(1) @Max(5) Integer education,
        @NotNull @StrictInt @Min(1) @Max(4) Integer environmentSatisfaction,
        @NotNull @StrictInt @Min(1) @Max(4) Integer jobInvolvement,
        @NotNull @StrictInt @Min(1) @Max(5) Integer jobLevel,
        @NotNull @StrictInt @Min(1) @Max(4) Integer jobSatisfaction,
        @NotNull @StrictInt @Min(1009) @Max(19999) Integer monthlyIncome,
        @NotNull @StrictInt @Min(0) @Max(9) Integer numCompaniesWorked,
        @NotNull @StrictInt @Min(11) @Max(25) Integer percentSalaryHike,
        @NotNull @StrictInt @Min(3) @Max(4) Integer performanceRating,
        @NotNull @StrictInt @Min(1) @Max(4) Integer relationshipSatisfaction,
        @NotNull @StrictInt @Min(0) @Max(3) Integer stockOptionLevel,
        @NotNull @StrictInt @Min(0) @Max(40) Integer totalWorkingYears,
        @NotNull @StrictInt @Min(0) @Max(6) Integer trainingTimesLastYear,
        @NotNull @StrictInt @Min(1) @Max(4) Integer workLifeBalance,
        @NotNull @StrictInt @Min(0) @Max(40) Integer yearsAtCompany,
        @NotNull @StrictInt @Min(0) @Max(18) Integer yearsInCurrentRole,
        @NotNull @StrictInt @Min(0) @Max(15) Integer yearsSinceLastPromotion,
        @NotNull @StrictInt @Min(0) @Max(17) Integer yearsWithCurrManager) {

    @JsonIgnore
    @AssertTrue(message = "L'ancienneté dans l'entreprise ne peut pas dépasser l'expérience totale")
    public boolean isYearsAtCompanyWithinCareer() {
        return yearsAtCompany == null || totalWorkingYears == null || yearsAtCompany <= totalWorkingYears;
    }

    @JsonIgnore
    @AssertTrue(message = "Les années dans le poste, avec le manager et depuis la dernière promotion "
            + "ne peuvent pas dépasser l'ancienneté dans l'entreprise")
    public boolean isYearsWithinCompany() {
        return yearsAtCompany == null
                || ((yearsInCurrentRole == null || yearsInCurrentRole <= yearsAtCompany)
                && (yearsWithCurrManager == null || yearsWithCurrManager <= yearsAtCompany)
                && (yearsSinceLastPromotion == null || yearsSinceLastPromotion <= yearsAtCompany));
    }

    @JsonIgnore
    @AssertTrue(message = "Ce poste n'existe pas dans le département choisi")
    public boolean isJobRoleInDepartment() {
        return department == null || jobRole == null || department.hasJobRole(jobRole);
    }
}
