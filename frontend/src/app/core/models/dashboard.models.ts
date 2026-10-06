import { Role } from './auth.models';
import { ApplicationStatus, ContractType, JobOfferStatus } from './common.models';
import { CvParsingStatus } from './cv.models';

/** Créations d'un mois (UTC), `month` au format AAAA-MM. */
export interface MonthlyCount {
  month: string;
  count: number;
}

export type CvState = 'ABSENT' | 'IMPORTE' | 'ANALYSE';

export interface CandidateTopMatch {
  jobOfferId: number;
  title: string;
  companyName: string;
  offerStatus: JobOfferStatus;
  score: number;
  computedAt: string;
  stale: boolean;
  applied: boolean;
}

/** GET /api/candidate/dashboard : statistiques du candidat connecté, calculées dans MySQL. */
export interface CandidateDashboard {
  totalApplications: number;
  applicationsByStatus: Record<ApplicationStatus, number>;
  publishedOffers: number;
  publishedOffersNotApplied: number;
  cv: {
    state: CvState;
    parsingStatus: CvParsingStatus | null;
    updatedAt: string | null;
    parsedAt: string | null;
    extractedSkills: number;
  };
  scoredOffers: number;
  topMatches: CandidateTopMatch[];
}

export interface RecruiterOfferStats {
  id: number;
  title: string;
  status: JobOfferStatus;
  createdAt: string;
  applications: number;
  applicationsByStatus: Record<ApplicationStatus, number>;
  scoredApplications: number;
  averageScore: number | null;
  bestScore: number | null;
}

export interface RecruiterTopCandidate {
  applicationId: number;
  candidateName: string;
  jobOfferId: number;
  jobTitle: string;
  score: number;
  status: ApplicationStatus;
  stale: boolean;
}

/** GET /api/recruiter/dashboard : offres et candidatures de l'entreprise du recruteur. */
export interface RecruiterDashboard {
  hasCompany: boolean;
  companyName: string | null;
  totalOffers: number;
  offersByStatus: Record<JobOfferStatus, number>;
  totalApplications: number;
  applicationsByStatus: Record<ApplicationStatus, number>;
  applicationsByMonth: MonthlyCount[];
  scoredApplications: number;
  averageMatchingScore: number | null;
  offers: RecruiterOfferStats[];
  topCandidates: RecruiterTopCandidate[];
}

export interface MonthlyActivity {
  month: string;
  newUsers: number;
  newOffers: number;
  applications: number;
}

/** GET /api/admin/dashboard : statistiques globales de la plateforme. */
export interface AdminDashboard {
  totalUsers: number;
  usersByRole: Record<Role, number>;
  disabledUsers: number;
  companies: number;
  recruitersWithoutCompany: number;
  totalOffers: number;
  offersByStatus: Record<JobOfferStatus, number>;
  publishedOffersByContract: Record<ContractType, number>;
  totalApplications: number;
  applicationsByStatus: Record<ApplicationStatus, number>;
  candidatesWithCv: number;
  cvsByParsingStatus: Record<CvParsingStatus, number>;
  storedMatchingScores: number;
  averageMatchingScore: number | null;
  topRequestedSkills: { skill: string; offers: number }[];
  activityByMonth: MonthlyActivity[];
}

/** GET /api/admin/ai/status : état du service IA vu par Spring Boot. */
export interface AiServiceStatus {
  status: string;
  service: string;
  version: string;
  modules: Record<string, boolean>;
  responseTimeMs: number;
}
