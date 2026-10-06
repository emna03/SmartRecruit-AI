"""Variables du modèle de turnover et correspondance avec les colonnes du dataset IBM HR Analytics.

Une seule définition, partagée par l'entraînement (``ml/train.py``) et la prédiction : le modèle reçoit
toujours exactement les mêmes colonnes, avec les mêmes valeurs que dans le dataset.

Colonnes écartées du dataset (35 colonnes au total, 24 variables explicatives conservées) :

- la cible ``Attrition`` ;
- constantes, sans information : ``EmployeeCount``, ``Over18``, ``StandardHours`` ;
- identifiant : ``EmployeeNumber`` ;
- variables sensibles (risque de discrimination, choix du projet) : ``Age``, ``Gender``, ``MaritalStatus`` ;
- taux non documentés par IBM et sans lien avec le salaire mensuel : ``DailyRate``, ``HourlyRate``, ``MonthlyRate``.
"""

TARGET = "Attrition"
POSITIVE_LABEL = "Yes"

DROPPED_CONSTANT = ("EmployeeCount", "Over18", "StandardHours")
DROPPED_IDENTIFIER = ("EmployeeNumber",)
DROPPED_SENSITIVE = ("Age", "Gender", "MaritalStatus")
DROPPED_UNDOCUMENTED = ("DailyRate", "HourlyRate", "MonthlyRate")

# Variables catégorielles : code de l'API -> valeur exacte du dataset (encodage one-hot).
CATEGORIES: dict[str, dict[str, str]] = {
    "BusinessTravel": {
        "NON_TRAVEL": "Non-Travel",
        "TRAVEL_RARELY": "Travel_Rarely",
        "TRAVEL_FREQUENTLY": "Travel_Frequently",
    },
    "Department": {
        "SALES": "Sales",
        "RESEARCH_DEVELOPMENT": "Research & Development",
        "HUMAN_RESOURCES": "Human Resources",
    },
    "EducationField": {
        "LIFE_SCIENCES": "Life Sciences",
        "MEDICAL": "Medical",
        "MARKETING": "Marketing",
        "TECHNICAL_DEGREE": "Technical Degree",
        "HUMAN_RESOURCES": "Human Resources",
        "OTHER": "Other",
    },
    "JobRole": {
        "SALES_EXECUTIVE": "Sales Executive",
        "SALES_REPRESENTATIVE": "Sales Representative",
        "RESEARCH_SCIENTIST": "Research Scientist",
        "LABORATORY_TECHNICIAN": "Laboratory Technician",
        "MANUFACTURING_DIRECTOR": "Manufacturing Director",
        "HEALTHCARE_REPRESENTATIVE": "Healthcare Representative",
        "RESEARCH_DIRECTOR": "Research Director",
        "MANAGER": "Manager",
        "HUMAN_RESOURCES": "Human Resources",
    },
    # Booléen dans l'API (true = heures supplémentaires).
    "OverTime": {"NO": "No", "YES": "Yes"},
}
CATEGORICAL = list(CATEGORIES)

# Variables numériques (entiers) et domaine accepté = valeurs minimale et maximale observées dans le dataset :
# le modèle n'a rien appris en dehors de ce domaine. Échelles ordinales documentées par IBM :
# Education 1 (Below College) à 5 (Doctor) ; satisfactions et implication 1 (Low) à 4 (Very High) ;
# WorkLifeBalance 1 (Bad) à 4 (Best) ; PerformanceRating 1 (Low) à 4 (Outstanding), seules les notes 3 et 4
# sont présentes dans le dataset.
NUMERIC_RANGES: dict[str, tuple[int, int]] = {
    "DistanceFromHome": (1, 29),
    "Education": (1, 5),
    "EnvironmentSatisfaction": (1, 4),
    "JobInvolvement": (1, 4),
    "JobLevel": (1, 5),
    "JobSatisfaction": (1, 4),
    "MonthlyIncome": (1009, 19999),
    "NumCompaniesWorked": (0, 9),
    "PercentSalaryHike": (11, 25),
    "PerformanceRating": (3, 4),
    "RelationshipSatisfaction": (1, 4),
    "StockOptionLevel": (0, 3),
    "TotalWorkingYears": (0, 40),
    "TrainingTimesLastYear": (0, 6),
    "WorkLifeBalance": (1, 4),
    "YearsAtCompany": (0, 40),
    "YearsInCurrentRole": (0, 18),
    "YearsSinceLastPromotion": (0, 15),
    "YearsWithCurrManager": (0, 17),
}
NUMERIC = list(NUMERIC_RANGES)

FEATURES = NUMERIC + CATEGORICAL

# Postes possibles par département (structure de l'entreprise fictive du dataset).
JOB_ROLES_BY_DEPARTMENT: dict[str, frozenset[str]] = {
    "SALES": frozenset({"SALES_EXECUTIVE", "SALES_REPRESENTATIVE", "MANAGER"}),
    "RESEARCH_DEVELOPMENT": frozenset({"RESEARCH_SCIENTIST", "LABORATORY_TECHNICIAN", "MANUFACTURING_DIRECTOR",
                                       "HEALTHCARE_REPRESENTATIVE", "RESEARCH_DIRECTOR", "MANAGER"}),
    "HUMAN_RESOURCES": frozenset({"HUMAN_RESOURCES", "MANAGER"}),
}

# Libellés affichés pour expliquer une prédiction.
LABELS: dict[str, str] = {
    "BusinessTravel": "Déplacements professionnels",
    "Department": "Département",
    "DistanceFromHome": "Distance domicile-travail",
    "Education": "Niveau d'études",
    "EducationField": "Domaine d'études",
    "EnvironmentSatisfaction": "Satisfaction vis-à-vis de l'environnement de travail",
    "JobInvolvement": "Implication dans le poste",
    "JobLevel": "Niveau hiérarchique",
    "JobRole": "Poste",
    "JobSatisfaction": "Satisfaction au travail",
    "MonthlyIncome": "Salaire mensuel",
    "NumCompaniesWorked": "Nombre d'entreprises précédentes",
    "OverTime": "Heures supplémentaires",
    "PercentSalaryHike": "Dernière augmentation (%)",
    "PerformanceRating": "Évaluation de la performance",
    "RelationshipSatisfaction": "Satisfaction relationnelle",
    "StockOptionLevel": "Niveau de stock-options",
    "TotalWorkingYears": "Années d'expérience totales",
    "TrainingTimesLastYear": "Formations l'an dernier",
    "WorkLifeBalance": "Équilibre vie pro / vie perso",
    "YearsAtCompany": "Ancienneté dans l'entreprise",
    "YearsInCurrentRole": "Années dans le poste actuel",
    "YearsSinceLastPromotion": "Années depuis la dernière promotion",
    "YearsWithCurrManager": "Années avec le manager actuel",
}


def api_name(column: str) -> str:
    """Nom du champ JSON correspondant à une colonne du dataset (``YearsAtCompany`` -> ``yearsAtCompany``)."""
    return column[0].lower() + column[1:]


def api_code(column: str, dataset_value: str) -> str:
    """Code de l'API correspondant à une valeur catégorielle du dataset."""
    for code, value in CATEGORIES[column].items():
        if value == dataset_value:
            return code
    raise ValueError(f"Valeur inconnue pour {column} : {dataset_value!r}")


def row_to_api(row: dict[str, object]) -> dict[str, object]:
    """Ligne du dataset (colonnes IBM) -> corps JSON de l'API (utilisé pour les exemples du rapport)."""
    payload: dict[str, object] = {}
    for column in NUMERIC:
        payload[api_name(column)] = int(row[column])
    for column in CATEGORICAL:
        code = api_code(column, str(row[column]))
        payload[api_name(column)] = code == "YES" if column == "OverTime" else code
    return payload
