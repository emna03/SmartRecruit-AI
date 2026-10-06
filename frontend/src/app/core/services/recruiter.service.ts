import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_URL } from '../api.config';
import { Company, CompanyRequest, RecruiterProfile, UpdateRecruiterProfileRequest } from '../models';

/** Profil du recruteur connecté et son entreprise. */
@Injectable({ providedIn: 'root' })
export class RecruiterService {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_URL}/recruiter`;

  getProfile(): Observable<RecruiterProfile> {
    return this.http.get<RecruiterProfile>(`${this.base}/profile`);
  }

  updateProfile(request: UpdateRecruiterProfileRequest): Observable<RecruiterProfile> {
    return this.http.put<RecruiterProfile>(`${this.base}/profile`, request);
  }

  /** 404 si le recruteur n'a pas encore créé son entreprise. */
  getCompany(): Observable<Company> {
    return this.http.get<Company>(`${this.base}/company`);
  }

  createCompany(request: CompanyRequest): Observable<Company> {
    return this.http.post<Company>(`${this.base}/company`, request);
  }

  updateCompany(request: CompanyRequest): Observable<Company> {
    return this.http.put<Company>(`${this.base}/company`, request);
  }
}
