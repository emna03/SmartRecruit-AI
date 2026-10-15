import { CandidateProfile } from './candidate.models';
import { EducationLevel } from './common.models';

export type CvParsingStatus = 'EN_COURS' | 'TERMINE' | 'ECHEC';
export type LanguageLevel = 'NATIF' | 'BILINGUE' | 'COURANT' | 'PROFESSIONNEL' | 'INTERMEDIAIRE' | 'DEBUTANT';

export interface DetectedSkill {
  name: string;
  category: string;
  occurrences: number;
}

/** Dates partielles telles qu'écrites sur le CV : « 2023 » ou « 2023-03 ». */
export interface CvEducationEntry {
  degree: string;
  fieldOfStudy: string | null;
  institution: string | null;
  startDate: string | null;
  endDate: string | null;
  level: EducationLevel | null;
}

export interface CvExperienceEntry {
  jobTitle: string;
  company: string | null;
  location: string | null;
  startDate: string | null;
  endDate: string | null;
  current: boolean;
  durationMonths: number | null;
  description: string | null;
}

export interface CvLanguageEntry {
  name: string;
  level: LanguageLevel | null;
  cefr: string | null;
  certification: string | null;
}

/** Résultat du CV Parser (validé par FastAPI puis par Spring Boot). */
export interface CvAnalysis {
  parserVersion: string;
  method: string;
  documentLanguage: 'fr' | 'en' | 'inconnu';
  pageCount: number;
  textLength: number;
  sectionsDetected: string[];
  skills: DetectedSkill[];
  technologies: DetectedSkill[];
  educations: CvEducationEntry[];
  experiences: CvExperienceEntry[];
  languages: CvLanguageEntry[];
  totalExperienceMonths: number | null;
  highestEducationLevel: EducationLevel | null;
  warnings: string[];
  processingTimeMs: number;
}

export interface CandidateCv {
  id: number;
  originalFileName: string;
  fileSize: number;
  parsingStatus: CvParsingStatus;
  parsingError: string | null;
  parsedAt: string | null;
  updatedAt: string | null;
  analysis: CvAnalysis | null;
}

export interface CvImportRequest {
  skills: boolean;
  educations: boolean;
  experiences: boolean;
}

export interface CvImportResult {
  skillsAdded: number;
  educationsAdded: number;
  experiencesAdded: number;
  skipped: number;
  educationLevelSet: boolean;
  yearsOfExperienceSet: boolean;
  profile: CandidateProfile;
}

/** Résumé professionnel généré par le LLM (Gemini), validé côté serveur. */
export interface ProfessionalSummary {
  summary: string;
  keywords: string[];
  model: string;
  provider: string;
  promptVersion: string;
  source: 'llm';
}

export const LANGUAGE_LEVEL_LABELS: Record<LanguageLevel, string> = {
  NATIF: 'Langue maternelle',
  BILINGUE: 'Bilingue',
  COURANT: 'Courant',
  PROFESSIONNEL: 'Professionnel',
  INTERMEDIAIRE: 'Intermédiaire',
  DEBUTANT: 'Notions',
};

export const CV_SECTION_LABELS: Partial<Record<string, string>> = {
  profil: 'Profil',
  experience: 'Expérience',
  formation: 'Formation',
  competences: 'Compétences',
  langues: 'Langues',
  projets: 'Projets',
  certifications: 'Certifications',
  interets: "Centres d'intérêt",
  contact: 'Contact',
};

export const DOCUMENT_LANGUAGE_LABELS: Record<CvAnalysis['documentLanguage'], string> = {
  fr: 'Français',
  en: 'Anglais',
  inconnu: 'Indéterminée',
};
