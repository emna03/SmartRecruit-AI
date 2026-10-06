import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_URL } from '../api.config';
import { Skill } from '../models';

@Injectable({ providedIn: 'root' })
export class SkillService {
  private readonly http = inject(HttpClient);

  search(query: string, limit = 8): Observable<Skill[]> {
    const params = new HttpParams().set('query', query).set('limit', limit);
    return this.http.get<Skill[]>(`${API_URL}/skills`, { params });
  }
}
