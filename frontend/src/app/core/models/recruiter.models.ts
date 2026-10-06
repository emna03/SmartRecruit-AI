export interface CompanySummary {
  id: number;
  name: string;
  sector: string | null;
  location: string | null;
  website: string | null;
}

export interface Company {
  id: number;
  name: string;
  description: string | null;
  sector: string | null;
  website: string | null;
  location: string | null;
  jobOfferCount: number;
  recruiterCount: number;
  createdAt: string;
}

export interface CompanyRequest {
  name: string;
  description: string | null;
  sector: string | null;
  website: string | null;
  location: string | null;
}

export interface RecruiterProfile {
  id: number;
  userId: number;
  email: string;
  firstName: string;
  lastName: string;
  jobTitle: string | null;
  phone: string | null;
  company: CompanySummary | null;
}

export interface UpdateRecruiterProfileRequest {
  firstName: string;
  lastName: string;
  jobTitle: string | null;
  phone: string | null;
}
