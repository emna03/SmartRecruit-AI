import { DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import {
  ClusteringService,
  ClusteringSummary,
  ClusteringTrainResult,
} from '../../core/services/clustering.service';

/** Résumé des clusters K-Means (admin : entraînement ; recruteur : consultation). */
@Component({
  selector: 'app-clustering-page',
  imports: [DecimalPipe],
  templateUrl: './clustering-page.html',
})
export class ClusteringPage implements OnInit {
  private readonly clustering = inject(ClusteringService);
  private readonly router = inject(Router);

  readonly summary = signal<ClusteringSummary | null>(null);
  readonly lastTrain = signal<ClusteringTrainResult | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly canTrain = signal(false);

  ngOnInit(): void {
    this.canTrain.set(this.router.url.includes('/admin/'));
    this.reload();
  }

  private role(): 'admin' | 'recruiter' {
    return this.canTrain() ? 'admin' : 'recruiter';
  }

  reload(): void {
    this.loading.set(true);
    this.error.set(null);
    this.clustering.summary(this.role()).subscribe({
      next: (s) => {
        this.summary.set(s);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Impossible de charger le résumé des clusters.');
        this.loading.set(false);
      },
    });
  }

  train(): void {
    if (!this.canTrain()) {
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.clustering.train().subscribe({
      next: (result) => {
        this.lastTrain.set(result);
        this.reload();
      },
      error: (err) => {
        const msg = err?.error?.message || 'Entraînement K-Means impossible.';
        this.error.set(msg);
        this.loading.set(false);
      },
    });
  }
}
