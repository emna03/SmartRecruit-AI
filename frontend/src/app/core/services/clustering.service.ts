import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_URL } from '../api.config';

export interface ClusterItem {
  clusterId: number;
  size: number;
  dominantTerms: string[];
  clusterDescription: string;
}

export interface ClusteringSummary {
  available: boolean;
  algorithm: string | null;
  modelVersion: string | null;
  embeddingModel: string | null;
  nClusters: number | null;
  nProfiles: number | null;
  silhouette: number | null;
  chosenK: number | null;
  trainedAt: string | null;
  clusters: ClusterItem[];
  note: string;
}

export interface ClusteringTrainResult {
  algorithm: string;
  modelVersion: string;
  embeddingModel: string;
  nClusters: number;
  nProfiles: number;
  silhouette: number;
  chosenK: number;
  trainedAt: string;
  clusters: ClusterItem[];
  note: string;
}

@Injectable({ providedIn: 'root' })
export class ClusteringService {
  private readonly http = inject(HttpClient);

  summary(role: 'admin' | 'recruiter'): Observable<ClusteringSummary> {
    return this.http.get<ClusteringSummary>(`${API_URL}/${role}/clustering/summary`);
  }

  train(): Observable<ClusteringTrainResult> {
    return this.http.post<ClusteringTrainResult>(`${API_URL}/admin/clustering/train`, null);
  }
}
