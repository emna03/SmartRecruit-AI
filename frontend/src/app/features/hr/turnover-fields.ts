import { JobRole, TurnoverDepartment, TurnoverFeature, TurnoverPredictionRequest } from '../../core/models';

export type FieldValue = string | number | boolean;

export interface FieldOption {
  value: FieldValue;
  label: string;
}

/** Champ du formulaire : liste de choix (options) ou entier borné (min/max = valeurs observées dans le dataset). */
export interface TurnoverField {
  key: TurnoverFeature;
  label: string;
  options?: FieldOption[];
  min?: number;
  max?: number;
  hint?: string;
}

export interface TurnoverSection {
  title: string;
  icon: string;
  fields: TurnoverField[];
}

const SATISFACTION: FieldOption[] = [
  { value: 1, label: '1 — Faible' },
  { value: 2, label: '2 — Moyenne' },
  { value: 3, label: '3 — Élevée' },
  { value: 4, label: '4 — Très élevée' },
];

export const DEPARTMENT_LABELS: Record<TurnoverDepartment, string> = {
  SALES: 'Ventes',
  RESEARCH_DEVELOPMENT: 'Recherche & Développement',
  HUMAN_RESOURCES: 'Ressources humaines',
};

export const JOB_ROLE_LABELS: Record<JobRole, string> = {
  SALES_EXECUTIVE: 'Commercial senior (Sales Executive)',
  SALES_REPRESENTATIVE: 'Représentant commercial',
  RESEARCH_SCIENTIST: 'Chercheur',
  LABORATORY_TECHNICIAN: 'Technicien de laboratoire',
  MANUFACTURING_DIRECTOR: 'Directeur de production',
  HEALTHCARE_REPRESENTATIVE: 'Représentant santé',
  RESEARCH_DIRECTOR: 'Directeur de recherche',
  MANAGER: 'Manager',
  HUMAN_RESOURCES: 'Chargé RH',
};

/** Postes existant dans chaque département de l'entreprise fictive du dataset (mêmes règles que le backend). */
export const JOB_ROLES_BY_DEPARTMENT: Record<TurnoverDepartment, JobRole[]> = {
  SALES: ['SALES_EXECUTIVE', 'SALES_REPRESENTATIVE', 'MANAGER'],
  RESEARCH_DEVELOPMENT: ['RESEARCH_SCIENTIST', 'LABORATORY_TECHNICIAN', 'MANUFACTURING_DIRECTOR', 'HEALTHCARE_REPRESENTATIVE', 'RESEARCH_DIRECTOR', 'MANAGER'],
  HUMAN_RESOURCES: ['HUMAN_RESOURCES', 'MANAGER'],
};

function options<K extends string>(labels: Record<K, string>): FieldOption[] {
  return (Object.keys(labels) as K[]).map((value) => ({ value, label: labels[value] }));
}

export const TURNOVER_SECTIONS: TurnoverSection[] = [
  {
    title: 'Poste et conditions de travail',
    icon: 'bi-briefcase',
    fields: [
      { key: 'department', label: 'Département', options: options(DEPARTMENT_LABELS) },
      { key: 'jobRole', label: 'Poste', options: options(JOB_ROLE_LABELS) },
      { key: 'jobLevel', label: 'Niveau hiérarchique', options: [1, 2, 3, 4, 5].map((v) => ({ value: v, label: `${v}` })), hint: '1 = le plus bas, 5 = le plus élevé' },
      {
        key: 'businessTravel',
        label: 'Déplacements professionnels',
        options: [
          { value: 'NON_TRAVEL', label: 'Aucun' },
          { value: 'TRAVEL_RARELY', label: 'Rares' },
          { value: 'TRAVEL_FREQUENTLY', label: 'Fréquents' },
        ],
      },
      { key: 'overTime', label: 'Heures supplémentaires', options: [{ value: true, label: 'Oui' }, { value: false, label: 'Non' }] },
      { key: 'distanceFromHome', label: 'Distance domicile-travail', min: 1, max: 29, hint: 'Unité non précisée par le dataset' },
    ],
  },
  {
    title: 'Carrière et rémunération',
    icon: 'bi-cash-coin',
    fields: [
      { key: 'monthlyIncome', label: 'Salaire mensuel', min: 1009, max: 19999, hint: 'Unité monétaire du dataset' },
      { key: 'percentSalaryHike', label: 'Dernière augmentation (%)', min: 11, max: 25 },
      { key: 'stockOptionLevel', label: 'Niveau de stock-options', options: [0, 1, 2, 3].map((v) => ({ value: v, label: `${v}` })) },
      { key: 'totalWorkingYears', label: "Années d'expérience totales", min: 0, max: 40 },
      { key: 'numCompaniesWorked', label: "Nombre d'entreprises précédentes", min: 0, max: 9 },
      { key: 'yearsAtCompany', label: "Ancienneté dans l'entreprise (années)", min: 0, max: 40 },
      { key: 'yearsInCurrentRole', label: 'Années dans le poste actuel', min: 0, max: 18 },
      { key: 'yearsSinceLastPromotion', label: 'Années depuis la dernière promotion', min: 0, max: 15 },
      { key: 'yearsWithCurrManager', label: 'Années avec le manager actuel', min: 0, max: 17 },
      { key: 'trainingTimesLastYear', label: "Formations suivies l'an dernier", min: 0, max: 6 },
    ],
  },
  {
    title: 'Formation',
    icon: 'bi-mortarboard',
    fields: [
      {
        key: 'education',
        label: "Niveau d'études",
        options: [
          { value: 1, label: '1 — Avant le supérieur' },
          { value: 2, label: '2 — Premier cycle' },
          { value: 3, label: '3 — Licence' },
          { value: 4, label: '4 — Master' },
          { value: 5, label: '5 — Doctorat' },
        ],
      },
      {
        key: 'educationField',
        label: "Domaine d'études",
        options: [
          { value: 'LIFE_SCIENCES', label: 'Sciences de la vie' },
          { value: 'MEDICAL', label: 'Médecine' },
          { value: 'MARKETING', label: 'Marketing' },
          { value: 'TECHNICAL_DEGREE', label: 'Diplôme technique' },
          { value: 'HUMAN_RESOURCES', label: 'Ressources humaines' },
          { value: 'OTHER', label: 'Autre' },
        ],
      },
    ],
  },
  {
    title: 'Satisfaction et évaluation',
    icon: 'bi-emoji-smile',
    fields: [
      { key: 'environmentSatisfaction', label: "Satisfaction vis-à-vis de l'environnement", options: SATISFACTION },
      { key: 'jobSatisfaction', label: 'Satisfaction au travail', options: SATISFACTION },
      { key: 'relationshipSatisfaction', label: 'Satisfaction relationnelle', options: SATISFACTION },
      { key: 'jobInvolvement', label: 'Implication dans le poste', options: SATISFACTION },
      {
        key: 'workLifeBalance',
        label: 'Équilibre vie pro / vie perso',
        options: [
          { value: 1, label: '1 — Mauvais' },
          { value: 2, label: '2 — Correct' },
          { value: 3, label: '3 — Bon' },
          { value: 4, label: '4 — Excellent' },
        ],
      },
      {
        key: 'performanceRating',
        label: 'Évaluation de la performance',
        options: [
          { value: 3, label: '3 — Excellente' },
          { value: 4, label: '4 — Remarquable' },
        ],
        hint: 'Seules les notes 3 et 4 existent dans le dataset',
      },
    ],
  },
];

export const TURNOVER_FIELDS: TurnoverField[] = TURNOVER_SECTIONS.flatMap((section) => section.fields);

/**
 * Deux employés fictifs du jeu de test du dataset IBM (lignes 1290 et 690 du fichier CSV), pour la démonstration.
 * Ce sont des données d'entrée uniquement : le score est toujours calculé par le modèle.
 */
export const EXAMPLE_PROFILES: { label: string; values: TurnoverPredictionRequest }[] = [
  {
    label: 'Exemple fictif n°1',
    values: {
      department: 'RESEARCH_DEVELOPMENT', jobRole: 'MANUFACTURING_DIRECTOR', jobLevel: 2, businessTravel: 'NON_TRAVEL', overTime: false,
      distanceFromHome: 2, monthlyIncome: 5762, percentSalaryHike: 14, stockOptionLevel: 1, totalWorkingYears: 15, numCompaniesWorked: 2,
      yearsAtCompany: 7, yearsInCurrentRole: 7, yearsSinceLastPromotion: 1, yearsWithCurrManager: 7, trainingTimesLastYear: 6,
      education: 2, educationField: 'MEDICAL', environmentSatisfaction: 2, jobSatisfaction: 4, relationshipSatisfaction: 3,
      jobInvolvement: 3, workLifeBalance: 3, performanceRating: 3,
    },
  },
  {
    label: 'Exemple fictif n°2',
    values: {
      department: 'SALES', jobRole: 'SALES_REPRESENTATIVE', jobLevel: 1, businessTravel: 'TRAVEL_RARELY', overTime: true,
      distanceFromHome: 21, monthlyIncome: 2121, percentSalaryHike: 13, stockOptionLevel: 0, totalWorkingYears: 1, numCompaniesWorked: 1,
      yearsAtCompany: 1, yearsInCurrentRole: 0, yearsSinceLastPromotion: 0, yearsWithCurrManager: 0, trainingTimesLastYear: 3,
      education: 3, educationField: 'OTHER', environmentSatisfaction: 4, jobSatisfaction: 2, relationshipSatisfaction: 2,
      jobInvolvement: 2, workLifeBalance: 4, performanceRating: 3,
    },
  },
];

/** Valeur lisible d'une variable (libellé du choix ou nombre). */
export function displayValue(feature: TurnoverFeature, value: FieldValue | null | undefined): string {
  if (value === null || value === undefined || value === '') {
    return '—';
  }
  const field = TURNOVER_FIELDS.find((f) => f.key === feature);
  const option = field?.options?.find((o) => o.value === value);
  if (option) {
    return option.label;
  }
  return typeof value === 'number' ? value.toLocaleString('fr-FR') : String(value);
}

/** Valeur de référence renvoyée par l'API (texte) -> valeur du formulaire. */
export function parseReference(feature: TurnoverFeature, reference: string): FieldValue {
  if (feature === 'overTime') {
    return reference === 'true';
  }
  const asNumber = Number(reference);
  return /^-?\d+$/.test(reference) && Number.isInteger(asNumber) ? asNumber : reference;
}
