import { EducationLevel } from './common.models';

export interface Education {
  id: number;
  degree: string;
  fieldOfStudy: string | null;
  institution: string;
  startDate: string | null;
  endDate: string | null;
  description: string | null;
}

export interface Experience {
  id: number;
  jobTitle: string;
  companyName: string;
  location: string | null;
  startDate: string;
  endDate: string | null;
  current: boolean;
  description: string | null;
}

export interface CandidateProfile {
  id: number;
  userId: number;
  email: string;
  firstName: string;
  lastName: string;
  headline: string | null;
  summary: string | null;
  phone: string | null;
  location: string | null;
  yearsOfExperience: number | null;
  educationLevel: EducationLevel | null;
  skills: string[];
  educations: Education[];
  experiences: Experience[];
  hasCv: boolean;
}

export interface UpdateCandidateProfileRequest {
  firstName: string;
  lastName: string;
  headline: string | null;
  summary: string | null;
  phone: string | null;
  location: string | null;
  yearsOfExperience: number | null;
  educationLevel: EducationLevel | null;
}

export type EducationRequest = Omit<Education, 'id'>;
export type ExperienceRequest = Omit<Experience, 'id' | 'current'>;
