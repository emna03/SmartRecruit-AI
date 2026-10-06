import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_URL } from '../api.config';
import { TurnoverPrediction, TurnoverPredictionRequest } from '../models';

/** Risque de turnover : Spring Boot valide la saisie puis interroge le modèle ML du service IA. Rien n'est enregistré. */
@Injectable({ providedIn: 'root' })
export class TurnoverService {
  private readonly http = inject(HttpClient);

  predict(request: TurnoverPredictionRequest): Observable<TurnoverPrediction> {
    return this.http.post<TurnoverPrediction>(`${API_URL}/hr/turnover/predictions`, request);
  }
}
