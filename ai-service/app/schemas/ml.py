"""Entrée et sortie de la prédiction du risque de turnover (JSON en camelCase).

L'entrée contient uniquement les 24 variables utilisées par le modèle : aucune donnée d'identité, ni âge, ni
genre, ni situation familiale. Tous les champs sont obligatoires, entiers stricts dans le domaine observé dans le
dataset d'entraînement, codes connus, et tout champ supplémentaire est refusé.
"""

from typing import Annotated, Literal

from pydantic import Field, StrictBool, StrictInt, model_validator

from app.schemas.cv import CamelModel
from app.schemas.matching import StrictCamelModel
from app.services.ml.features import CATEGORIES, JOB_ROLES_BY_DEPARTMENT, NUMERIC_RANGES


def _bounded(column: str):
    low, high = NUMERIC_RANGES[column]
    return Annotated[StrictInt, Field(ge=low, le=high)]


BusinessTravel = Literal["NON_TRAVEL", "TRAVEL_RARELY", "TRAVEL_FREQUENTLY"]
Department = Literal["SALES", "RESEARCH_DEVELOPMENT", "HUMAN_RESOURCES"]
EducationField = Literal["LIFE_SCIENCES", "MEDICAL", "MARKETING", "TECHNICAL_DEGREE", "HUMAN_RESOURCES", "OTHER"]
JobRole = Literal["SALES_EXECUTIVE", "SALES_REPRESENTATIVE", "RESEARCH_SCIENTIST", "LABORATORY_TECHNICIAN",
                  "MANUFACTURING_DIRECTOR", "HEALTHCARE_REPRESENTATIVE", "RESEARCH_DIRECTOR", "MANAGER",
                  "HUMAN_RESOURCES"]
RiskLevel = Literal["FAIBLE", "MOYEN", "ELEVE"]
ModelName = Literal["LOGISTIC_REGRESSION", "RANDOM_FOREST", "XGBOOST"]
Rate = Annotated[float, Field(ge=0, le=1)]


class TurnoverPredictionRequest(StrictCamelModel):
    business_travel: BusinessTravel
    department: Department
    education_field: EducationField
    job_role: JobRole
    over_time: StrictBool
    distance_from_home: _bounded("DistanceFromHome")
    education: _bounded("Education")
    environment_satisfaction: _bounded("EnvironmentSatisfaction")
    job_involvement: _bounded("JobInvolvement")
    job_level: _bounded("JobLevel")
    job_satisfaction: _bounded("JobSatisfaction")
    monthly_income: _bounded("MonthlyIncome")
    num_companies_worked: _bounded("NumCompaniesWorked")
    percent_salary_hike: _bounded("PercentSalaryHike")
    performance_rating: _bounded("PerformanceRating")
    relationship_satisfaction: _bounded("RelationshipSatisfaction")
    stock_option_level: _bounded("StockOptionLevel")
    total_working_years: _bounded("TotalWorkingYears")
    training_times_last_year: _bounded("TrainingTimesLastYear")
    work_life_balance: _bounded("WorkLifeBalance")
    years_at_company: _bounded("YearsAtCompany")
    years_in_current_role: _bounded("YearsInCurrentRole")
    years_since_last_promotion: _bounded("YearsSinceLastPromotion")
    years_with_curr_manager: _bounded("YearsWithCurrManager")

    @model_validator(mode="after")
    def _check_consistency(self) -> "TurnoverPredictionRequest":
        if self.years_at_company > self.total_working_years:
            raise ValueError("L'ancienneté dans l'entreprise ne peut pas dépasser l'expérience totale")
        for name, label in (("years_in_current_role", "Les années dans le poste actuel"),
                            ("years_with_curr_manager", "Les années avec le manager actuel"),
                            ("years_since_last_promotion", "Les années depuis la dernière promotion")):
            if getattr(self, name) > self.years_at_company:
                raise ValueError(f"{label} ne peuvent pas dépasser l'ancienneté dans l'entreprise")
        if self.job_role not in JOB_ROLES_BY_DEPARTMENT[self.department]:
            raise ValueError("Ce poste n'existe pas dans le département choisi")
        return self

    def to_row(self) -> dict[str, object]:
        """Valeurs au format des colonnes du dataset, dans l'ordre attendu par le modèle."""
        return {
            "DistanceFromHome": self.distance_from_home,
            "Education": self.education,
            "EnvironmentSatisfaction": self.environment_satisfaction,
            "JobInvolvement": self.job_involvement,
            "JobLevel": self.job_level,
            "JobSatisfaction": self.job_satisfaction,
            "MonthlyIncome": self.monthly_income,
            "NumCompaniesWorked": self.num_companies_worked,
            "PercentSalaryHike": self.percent_salary_hike,
            "PerformanceRating": self.performance_rating,
            "RelationshipSatisfaction": self.relationship_satisfaction,
            "StockOptionLevel": self.stock_option_level,
            "TotalWorkingYears": self.total_working_years,
            "TrainingTimesLastYear": self.training_times_last_year,
            "WorkLifeBalance": self.work_life_balance,
            "YearsAtCompany": self.years_at_company,
            "YearsInCurrentRole": self.years_in_current_role,
            "YearsSinceLastPromotion": self.years_since_last_promotion,
            "YearsWithCurrManager": self.years_with_curr_manager,
            "BusinessTravel": CATEGORIES["BusinessTravel"][self.business_travel],
            "Department": CATEGORIES["Department"][self.department],
            "EducationField": CATEGORIES["EducationField"][self.education_field],
            "JobRole": CATEGORIES["JobRole"][self.job_role],
            "OverTime": CATEGORIES["OverTime"]["YES" if self.over_time else "NO"],
        }


class TurnoverFactor(CamelModel):
    feature: Annotated[str, Field(min_length=1, max_length=40)]
    label: Annotated[str, Field(min_length=1, max_length=80)]
    # Log-odds (méthode LINEAR) ou points de probabilité (méthode OCCLUSION) ; le signe donne le sens.
    impact: float
    direction: Literal["INCREASES_RISK", "DECREASES_RISK"]
    # Méthode OCCLUSION : valeur de comparaison (même format que la requête : code, nombre ou true/false).
    reference_value: Annotated[str, Field(min_length=1, max_length=40)] | None = None


class TurnoverModelInfo(CamelModel):
    name: ModelName
    version: Annotated[str, Field(min_length=1, max_length=20)]
    dataset: Annotated[str, Field(min_length=1, max_length=100)]
    explanation_method: Literal["LINEAR", "OCCLUSION"]
    test_accuracy: Rate
    test_precision: Rate
    test_recall: Rate
    test_f1: Rate
    test_roc_auc: Rate


class TurnoverPredictionResponse(CamelModel):
    probability: Rate
    risk_level: RiskLevel
    medium_threshold: Rate
    high_threshold: Rate
    factors: Annotated[list[TurnoverFactor], Field(max_length=5)]
    model: TurnoverModelInfo
