import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_URL } from '../api.config';
import { JobOffer, JobOfferRequest, JobOfferStatus, JobSearchCriteria, PageResponse } from '../models';

/** Offres d'emploi : recherche publique et gestion par le recruteur. */
@Injectable({ providedIn: 'root' })
export class JobOfferService {
  private readonly http = inject(HttpClient);

  search(criteria: JobSearchCriteria = {}): Observable<PageResponse<JobOffer>> {
    let params = new HttpParams();
    for (const [key, value] of Object.entries(criteria)) {
      if (value !== null && value !== undefined && `${value}`.trim() !== '') {
        params = params.set(key, `${value}`.trim());
      }
    }
    return this.http.get<PageResponse<JobOffer>>(`${API_URL}/jobs`, { params });
  }

  getPublished(id: number): Observable<JobOffer> {
    return this.http.get<JobOffer>(`${API_URL}/jobs/${id}`);
  }

  findMine(): Observable<JobOffer[]> {
    return this.http.get<JobOffer[]>(`${API_URL}/recruiter/jobs`);
  }

  getMine(id: number): Observable<JobOffer> {
    return this.http.get<JobOffer>(`${API_URL}/recruiter/jobs/${id}`);
  }

  create(request: JobOfferRequest): Observable<JobOffer> {
    return this.http.post<JobOffer>(`${API_URL}/recruiter/jobs`, request);
  }

  update(id: number, request: JobOfferRequest): Observable<JobOffer> {
    return this.http.put<JobOffer>(`${API_URL}/recruiter/jobs/${id}`, request);
  }

  updateStatus(id: number, status: JobOfferStatus): Observable<JobOffer> {
    return this.http.patch<JobOffer>(`${API_URL}/recruiter/jobs/${id}/status`, { status });
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${API_URL}/recruiter/jobs/${id}`);
  }
}
