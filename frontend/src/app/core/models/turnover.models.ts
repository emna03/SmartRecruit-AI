export type BusinessTravel = 'NON_TRAVEL' | 'TRAVEL_RARELY' | 'TRAVEL_FREQUENTLY';
export type TurnoverDepartment = 'SALES' | 'RESEARCH_DEVELOPMENT' | 'HUMAN_RESOURCES';
export type EducationField = 'LIFE_SCIENCES' | 'MEDICAL' | 'MARKETING' | 'TECHNICAL_DEGREE' | 'HUMAN_RESOURCES' | 'OTHER';
export type JobRole =
  | 'SALES_EXECUTIVE'
  | 'SALES_REPRESENTATIVE'
  | 'RESEARCH_SCIENTIST'
  | 'LABORATORY_TECHNICIAN'
  | 'MANUFACTURING_DIRECTOR'
  | 'HEALTHCARE_REPRESENTATIVE'
  | 'RESEARCH_DIRECTOR'
  | 'MANAGER'
  | 'HUMAN_RESOURCES';
export type RiskLevel = 'FAIBLE' | 'MOYEN' | 'ELEVE';

/** Les 24 variables du modèle (dataset IBM HR Analytics) : aucune donnée d'identité, ni âge, genre ou situation familiale. */
export interface TurnoverPredictionRequest {
  businessTravel: BusinessTravel;
  department: TurnoverDepartment;
  educationField: EducationField;
  jobRole: JobRole;
  overTime: boolean;
  distanceFromHome: number;
  education: number;
  environmentSatisfaction: number;
  jobInvolvement: number;
  jobLevel: number;
  jobSatisfaction: number;
  monthlyIncome: number;
  numCompaniesWorked: number;
  percentSalaryHike: number;
  performanceRating: number;
  relationshipSatisfaction: number;
  stockOptionLevel: number;
  totalWorkingYears: number;
  trainingTimesLastYear: number;
  workLifeBalance: number;
  yearsAtCompany: number;
  yearsInCurrentRole: number;
  yearsSinceLastPromotion: number;
  yearsWithCurrManager: number;
}

export type TurnoverFeature = keyof TurnoverPredictionRequest;

/** Variable ayant influencé le score (comportement du modèle, pas une causalité). */
export interface TurnoverFactor {
  feature: TurnoverFeature;
  label: string;
  /** OCCLUSION : points de probabilité ; LINEAR : contribution au logit. Le signe donne le sens. */
  impact: number;
  direction: 'INCREASES_RISK' | 'DECREASES_RISK';
  /** OCCLUSION : valeur de comparaison, au format de la requête (code, nombre, true/false). */
  referenceValue: string | null;
}

export interface TurnoverModelInfo {
  name: 'LOGISTIC_REGRESSION' | 'RANDOM_FOREST' | 'XGBOOST';
  version: string;
  dataset: string;
  explanationMethod: 'LINEAR' | 'OCCLUSION';
  testAccuracy: number;
  testPrecision: number;
  testRecall: number;
  testF1: number;
  testRocAuc: number;
}

export interface TurnoverPrediction {
  probability: number;
  riskLevel: RiskLevel;
  mediumThreshold: number;
  highThreshold: number;
  factors: TurnoverFactor[];
  model: TurnoverModelInfo;
}

export const TURNOVER_DISCLAIMER =
  'Cette prédiction est une estimation produite par un modèle entraîné sur un dataset de démonstration et ne doit pas être utilisée seule pour prendre une décision RH.';
