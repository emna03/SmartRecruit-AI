import { ContractType, EducationLevel } from './common.models';

export type ChatbotIntent =
  | 'GREETING'
  | 'THANKS'
  | 'HELP'
  | 'SEARCH_OFFERS'
  | 'OFFER_SKILLS'
  | 'OFFER_EXPERIENCE'
  | 'OFFER_EDUCATION'
  | 'OFFER_DETAILS'
  | 'OFFER_SALARY'
  | 'FAQ_APPLY'
  | 'FAQ_CV'
  | 'FAQ_MATCHING'
  | 'FAQ_APPLICATIONS'
  | 'FAQ_PROFILE'
  | 'FAQ_PRIVACY'
  | 'FAQ_RECOMMENDATION'
  | 'UNKNOWN';

/** Lien proposé avec une réponse, associé à une page de l'application. */
export type ChatbotAction = 'OPEN_JOBS' | 'OPEN_CV' | 'OPEN_APPLICATIONS' | 'OPEN_PROFILE';

/** Offre citée par le chatbot (données issues de la base, jamais inventées). */
export interface ChatbotOffer {
  id: number;
  title: string;
  companyName: string;
  location: string | null;
  contractType: ContractType;
  minExperienceYears: number;
  educationLevel: EducationLevel | null;
  requiredSkills: string[];
}

/** Informations comprises dans la question. */
export interface ChatbotEntities {
  skills: string[];
  experienceYears: number | null;
  educationLevel: EducationLevel | null;
  contractType: ContractType | null;
  location: string | null;
  keywords: string[];
}

export interface ChatbotReply {
  intent: ChatbotIntent;
  answer: string;
  confidence: number;
  offers: ChatbotOffer[];
  totalMatches: number;
  contextOffer: ChatbotOffer | null;
  entities: ChatbotEntities;
  suggestions: string[];
  actions: ChatbotAction[];
  chatbotVersion: string;
}

export interface ChatbotQuestion {
  message: string;
  jobOfferId: number | null;
}
