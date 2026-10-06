import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_URL } from '../api.config';
import { CandidateMatching, MatchingRefresh } from '../models';

/** Score de compatibilité CV ↔ offre, calculé par le service IA via Spring Boot. */
@Injectable({ providedIn: 'root' })
export class MatchingService {
  private readonly http = inject(HttpClient);

  /** Compatibilité du candidat connecté avec une offre (recalculée à partir de son CV analysé). */
  getForOffer(jobId: number): Observable<CandidateMatching> {
    return this.http.get<CandidateMatching>(`${API_URL}/candidate/jobs/${jobId}/matching`);
  }

  /** Recalcule les scores des candidats d'une offre (recruteur) et renvoie la liste classée. */
  refreshForOffer(jobId: number): Observable<MatchingRefresh> {
    return this.http.post<MatchingRefresh>(`${API_URL}/recruiter/jobs/${jobId}/matching`, null);
  }
}
