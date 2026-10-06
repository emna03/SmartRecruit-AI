import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_URL } from '../api.config';
import { Company, JobOffer, JobOfferStatus, Role, User } from '../models';

/** Endpoints d'administration (rôle ADMIN). */
@Injectable({ providedIn: 'root' })
export class AdminService {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_URL}/admin`;

  findUsers(role?: Role | null): Observable<User[]> {
    const params = role ? new HttpParams().set('role', role) : undefined;
    return this.http.get<User[]>(`${this.base}/users`, { params });
  }

  setUserEnabled(id: number, enabled: boolean): Observable<User> {
    return this.http.patch<User>(`${this.base}/users/${id}/status`, { enabled });
  }

  deleteUser(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/users/${id}`);
  }

  findCompanies(): Observable<Company[]> {
    return this.http.get<Company[]>(`${this.base}/companies`);
  }

  deleteCompany(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/companies/${id}`);
  }

  findJobs(status?: JobOfferStatus | null): Observable<JobOffer[]> {
    const params = status ? new HttpParams().set('status', status) : undefined;
    return this.http.get<JobOffer[]>(`${this.base}/jobs`, { params });
  }

  updateJobStatus(id: number, status: JobOfferStatus): Observable<JobOffer> {
    return this.http.patch<JobOffer>(`${this.base}/jobs/${id}/status`, { status });
  }

  deleteJob(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/jobs/${id}`);
  }
}
