import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_URL } from '../api.config';
import { AdminDashboard, CandidateDashboard, RecruiterDashboard } from '../models';

/** Statistiques des tableaux de bord : le périmètre (candidat, entreprise) est déduit du jeton par le backend. */
@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly http = inject(HttpClient);

  candidate(): Observable<CandidateDashboard> {
    return this.http.get<CandidateDashboard>(`${API_URL}/candidate/dashboard`);
  }

  recruiter(): Observable<RecruiterDashboard> {
    return this.http.get<RecruiterDashboard>(`${API_URL}/recruiter/dashboard`);
  }

  admin(): Observable<AdminDashboard> {
    return this.http.get<AdminDashboard>(`${API_URL}/admin/dashboard`);
  }
}
