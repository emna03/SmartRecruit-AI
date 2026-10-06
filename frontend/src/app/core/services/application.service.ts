import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_URL } from '../api.config';
import { ApplicationDetail, ApplicationStatus, CandidateApplication, RecruiterApplication } from '../models';

/** Candidatures : côté candidat (postuler, suivre) et côté recruteur (consulter, changer le statut). */
@Injectable({ providedIn: 'root' })
export class ApplicationService {
  private readonly http = inject(HttpClient);

  apply(jobOfferId: number, coverLetter: string | null): Observable<CandidateApplication> {
    return this.http.post<CandidateApplication>(`${API_URL}/candidate/applications`, { jobOfferId, coverLetter });
  }

  findMine(): Observable<CandidateApplication[]> {
    return this.http.get<CandidateApplication[]>(`${API_URL}/candidate/applications`);
  }

  findForOffer(jobOfferId: number): Observable<RecruiterApplication[]> {
    return this.http.get<RecruiterApplication[]>(`${API_URL}/recruiter/jobs/${jobOfferId}/applications`);
  }

  getForRecruiter(id: number): Observable<ApplicationDetail> {
    return this.http.get<ApplicationDetail>(`${API_URL}/recruiter/applications/${id}`);
  }

  updateStatus(id: number, status: ApplicationStatus): Observable<RecruiterApplication> {
    return this.http.patch<RecruiterApplication>(`${API_URL}/recruiter/applications/${id}/status`, { status });
  }
}
