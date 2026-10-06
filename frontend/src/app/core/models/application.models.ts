import { CandidateProfile } from './candidate.models';
import { ApplicationStatus, ContractType, EducationLevel, JobOfferStatus } from './common.models';

/** Candidature vue par le candidat. */
export interface CandidateApplication {
  id: number;
  status: ApplicationStatus;
  coverLetter: string | null;
  appliedAt: string;
  updatedAt: string;
  job: {
    id: number;
    title: string;
    companyName: string;
    location: string | null;
    contractType: ContractType;
    status: JobOfferStatus;
  };
}

/** Candidature dans la liste du recruteur. */
export interface RecruiterApplication {
  id: number;
  jobOfferId: number;
  jobTitle: string;
  status: ApplicationStatus;
  appliedAt: string;
  candidate: {
    candidateId: number;
    firstName: string;
    lastName: string;
    email: string;
    headline: string | null;
    location: string | null;
    yearsOfExperience: number | null;
    educationLevel: EducationLevel | null;
    skills: string[];
    hasCv: boolean;
  };
}

/** Détail d'une candidature pour le recruteur (profil complet). */
export interface ApplicationDetail {
  id: number;
  jobOfferId: number;
  jobTitle: string;
  status: ApplicationStatus;
  coverLetter: string | null;
  appliedAt: string;
  updatedAt: string;
  candidate: CandidateProfile;
}
