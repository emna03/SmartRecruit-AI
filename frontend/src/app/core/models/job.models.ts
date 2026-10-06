import { ContractType, EducationLevel, JobOfferStatus } from './common.models';
import { CompanySummary } from './recruiter.models';

export interface JobOffer {
  id: number;
  title: string;
  description: string;
  location: string | null;
  contractType: ContractType;
  minExperienceYears: number;
  educationLevel: EducationLevel | null;
  salaryMin: number | null;
  salaryMax: number | null;
  status: JobOfferStatus;
  requiredSkills: string[];
  company: CompanySummary;
  /** Présent uniquement pour le recruteur propriétaire et l'admin. */
  applicationCount?: number;
  createdAt: string;
  updatedAt: string;
}

export interface JobOfferRequest {
  title: string;
  description: string;
  location: string | null;
  contractType: ContractType;
  minExperienceYears: number;
  educationLevel: EducationLevel | null;
  salaryMin: number | null;
  salaryMax: number | null;
  requiredSkills: string[];
  status?: JobOfferStatus;
}

export interface JobSearchCriteria {
  keyword?: string | null;
  location?: string | null;
  contractType?: ContractType | null;
  skill?: string | null;
  educationLevel?: EducationLevel | null;
  maxExperienceYears?: number | null;
  companyId?: number | null;
  page?: number;
  size?: number;
}
