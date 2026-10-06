export type EducationLevel = 'BAC' | 'BAC_PLUS_2' | 'BAC_PLUS_3' | 'BAC_PLUS_5' | 'DOCTORAT';
export type ContractType = 'CDI' | 'CDD' | 'STAGE' | 'ALTERNANCE' | 'FREELANCE';
export type JobOfferStatus = 'BROUILLON' | 'PUBLIEE' | 'FERMEE';
export type ApplicationStatus = 'EN_ATTENTE' | 'PRESELECTIONNE' | 'ACCEPTE' | 'REFUSE';

/** Format d'erreur unique renvoyé par le backend. */
export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  fieldErrors?: Record<string, string>;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface Skill {
  id: number;
  name: string;
  category: string | null;
}
