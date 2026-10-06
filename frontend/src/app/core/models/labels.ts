import { Role } from './auth.models';
import { ApplicationStatus, ContractType, EducationLevel, JobOfferStatus } from './common.models';

export const ROLE_LABELS: Record<Role, string> = {
  ADMIN: 'Administrateur',
  RECRUTEUR: 'Recruteur',
  CANDIDAT: 'Candidat',
};

export const CONTRACT_TYPE_LABELS: Record<ContractType, string> = {
  CDI: 'CDI',
  CDD: 'CDD',
  STAGE: 'Stage',
  ALTERNANCE: 'Alternance',
  FREELANCE: 'Freelance',
};

export const EDUCATION_LEVEL_LABELS: Record<EducationLevel, string> = {
  BAC: 'Bac',
  BAC_PLUS_2: 'Bac+2',
  BAC_PLUS_3: 'Bac+3 (Licence)',
  BAC_PLUS_5: 'Bac+5 (Master / Ingénieur)',
  DOCTORAT: 'Doctorat',
};

export const JOB_STATUS_LABELS: Record<JobOfferStatus, string> = {
  BROUILLON: 'Brouillon',
  PUBLIEE: 'Publiée',
  FERMEE: 'Fermée',
};

export const APPLICATION_STATUS_LABELS: Record<ApplicationStatus, string> = {
  EN_ATTENTE: 'En attente',
  PRESELECTIONNE: 'Présélectionné',
  ACCEPTE: 'Accepté',
  REFUSE: 'Refusé',
};

/** Options de listes déroulantes, dans l'ordre de déclaration. */
export function optionsOf<K extends string>(labels: Record<K, string>): { value: K; label: string }[] {
  return (Object.keys(labels) as K[]).map((value) => ({ value, label: labels[value] }));
}
