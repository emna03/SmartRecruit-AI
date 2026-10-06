import { RecruiterApplication } from './application.models';

/** EXACT : même compétence ; IMPLIED : couverte par une compétence plus précise ; RELATED : technologie proche. */
export type MatchType = 'EXACT' | 'IMPLIED' | 'RELATED';

/** Compétence requise par l'offre et compétence du CV qui la couvre. */
export interface MatchedSkill {
  name: string;
  matchedBy: string;
  matchType: MatchType;
  /** 1 = correspondance complète, 0,5 = technologie proche. */
  credit: number;
}

/** Score de compatibilité CV ↔ offre (0 à 100) avec ses sous-scores et son explication. */
export interface MatchingResult {
  score: number;
  skillsScore: number;
  /** null : l'offre n'exige pas d'expérience (critère non pris en compte). */
  experienceScore: number | null;
  /** null : l'offre n'indique pas de niveau d'études (critère non pris en compte). */
  educationScore: number | null;
  matchedSkills: MatchedSkill[];
  missingSkills: string[];
  explanation: string;
  algorithmVersion: string;
  /** Date UTC du calcul. */
  computedAt: string;
  /** Le CV a changé depuis le calcul. */
  stale: boolean;
}

export type CandidateMatchingStatus = 'CALCULE' | 'CV_ABSENT' | 'CV_NON_ANALYSE' | 'IA_INDISPONIBLE';

export interface CandidateMatching {
  status: CandidateMatchingStatus;
  message: string;
  result: MatchingResult | null;
}

export interface MatchingRefresh {
  computed: number;
  withoutAnalyzedCv: number;
  applications: RecruiterApplication[];
}
