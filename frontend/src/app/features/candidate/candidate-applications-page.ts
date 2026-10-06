import { DatePipe } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { errorMessage } from '../../core/http-errors';
import { APPLICATION_STATUS_LABELS, ApplicationStatus, CandidateApplication, optionsOf } from '../../core/models';
import { ApplicationService } from '../../core/services/application.service';
import { LabelPipe } from '../../shared/label.pipe';
import { LoadingSpinner } from '../../shared/loading-spinner';
import { StatusBadge } from '../../shared/status-badge';

@Component({
  selector: 'app-candidate-applications-page',
  imports: [RouterLink, DatePipe, LabelPipe, LoadingSpinner, StatusBadge],
  template: `
    <h1 class="page-title">Mes candidatures</h1>
    <p class="page-subtitle">Suivez l'avancement de vos candidatures.</p>

    @if (loading()) {
      <app-loading-spinner />
    } @else if (error()) {
      <div class="alert alert-danger">{{ error() }}</div>
    } @else if (applications().length === 0) {
      <div class="card">
        <div class="empty-state">
          <i aria-hidden="true" class="bi bi-send"></i>Vous n'avez encore envoyé aucune candidature.
          <div><a class="btn btn-primary btn-sm mt-2" routerLink="/candidate/jobs">Parcourir les offres</a></div>
        </div>
      </div>
    } @else {
      <div class="d-flex flex-wrap gap-2 mb-3" role="group" aria-label="Filtrer par statut">
        <button type="button" class="btn btn-sm" [class]="filter() === null ? 'btn btn-sm btn-primary' : 'btn btn-sm btn-outline-secondary'"
                (click)="filter.set(null)">Toutes ({{ applications().length }})</button>
        @for (o of statusOptions; track o.value) {
          <button type="button" [class]="filter() === o.value ? 'btn btn-sm btn-primary' : 'btn btn-sm btn-outline-secondary'"
                  (click)="filter.set(o.value)">{{ o.label }} ({{ countOf(o.value) }})</button>
        }
      </div>

      <div class="card">
        <div class="table-responsive">
          <table class="table table-hover align-middle mb-0" data-testid="applications-table">
            <thead class="table-light">
              <tr><th>Offre</th><th>Entreprise</th><th class="d-none d-md-table-cell">Contrat</th><th>Envoyée le</th><th>Statut</th></tr>
            </thead>
            <tbody>
              @for (a of filtered(); track a.id) {
                <tr>
                  <td>
                    @if (a.job.status === 'PUBLIEE') {
                      <a [routerLink]="['/candidate/jobs', a.job.id]" class="fw-semibold">{{ a.job.title }}</a>
                    } @else {
                      <span class="fw-semibold">{{ a.job.title }}</span>
                      <span class="badge text-bg-light ms-1">{{ a.job.status | label: 'jobStatus' }}</span>
                    }
                    @if (a.coverLetter) {
                      <details class="small text-secondary mt-1"><summary>Lettre de motivation</summary><p class="text-pre-line mb-0 mt-1">{{ a.coverLetter }}</p></details>
                    }
                  </td>
                  <td>{{ a.job.companyName }}</td>
                  <td class="d-none d-md-table-cell">{{ a.job.contractType | label: 'contract' }}</td>
                  <td>{{ a.appliedAt | date: 'dd/MM/yyyy' }}</td>
                  <td><app-status-badge [status]="a.status" /></td>
                </tr>
              } @empty {
                <tr><td colspan="5" class="text-center text-secondary py-4">Aucune candidature avec ce statut.</td></tr>
              }
            </tbody>
          </table>
        </div>
      </div>
    }
  `,
})
export class CandidateApplicationsPage implements OnInit {
  private readonly applicationService = inject(ApplicationService);

  readonly statusOptions = optionsOf(APPLICATION_STATUS_LABELS);
  readonly applications = signal<CandidateApplication[]>([]);
  readonly filter = signal<ApplicationStatus | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  readonly filtered = computed(() => {
    const status = this.filter();
    return status ? this.applications().filter((a) => a.status === status) : this.applications();
  });

  ngOnInit(): void {
    this.applicationService.findMine().subscribe({
      next: (list) => {
        this.applications.set(list);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(errorMessage(err, 'Impossible de charger vos candidatures.'));
        this.loading.set(false);
      },
    });
  }

  countOf(status: ApplicationStatus): number {
    return this.applications().filter((a) => a.status === status).length;
  }
}
