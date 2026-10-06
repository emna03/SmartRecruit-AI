import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_URL } from '../api.config';
import {
  CandidateProfile,
  EducationRequest,
  ExperienceRequest,
  UpdateCandidateProfileRequest,
} from '../models';

/** Profil du candidat connecté. Chaque modification renvoie le profil complet à jour. */
@Injectable({ providedIn: 'root' })
export class CandidateProfileService {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_URL}/candidate/profile`;

  get(): Observable<CandidateProfile> {
    return this.http.get<CandidateProfile>(this.base);
  }

  update(request: UpdateCandidateProfileRequest): Observable<CandidateProfile> {
    return this.http.put<CandidateProfile>(this.base, request);
  }

  replaceSkills(skills: string[]): Observable<CandidateProfile> {
    return this.http.put<CandidateProfile>(`${this.base}/skills`, { skills });
  }

  addEducation(request: EducationRequest): Observable<CandidateProfile> {
    return this.http.post<CandidateProfile>(`${this.base}/educations`, request);
  }

  updateEducation(id: number, request: EducationRequest): Observable<CandidateProfile> {
    return this.http.put<CandidateProfile>(`${this.base}/educations/${id}`, request);
  }

  deleteEducation(id: number): Observable<CandidateProfile> {
    return this.http.delete<CandidateProfile>(`${this.base}/educations/${id}`);
  }

  addExperience(request: ExperienceRequest): Observable<CandidateProfile> {
    return this.http.post<CandidateProfile>(`${this.base}/experiences`, request);
  }

  updateExperience(id: number, request: ExperienceRequest): Observable<CandidateProfile> {
    return this.http.put<CandidateProfile>(`${this.base}/experiences/${id}`, request);
  }

  deleteExperience(id: number): Observable<CandidateProfile> {
    return this.http.delete<CandidateProfile>(`${this.base}/experiences/${id}`);
  }
}
