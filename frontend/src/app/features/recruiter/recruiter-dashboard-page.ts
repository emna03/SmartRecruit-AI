import { DatePipe } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { of, switchMap } from 'rxjs';
import { errorMessage } from '../../core/http-errors';
import { JobOffer, RecruiterProfile } from '../../core/models';
import { JobOfferService } from '../../core/services/job-offer.service';
import { RecruiterService } from '../../core/services/recruiter.service';
import { LoadingSpinner } from '../../shared/loading-spinner';
import { StatusBadge } from '../../shared/status-badge';
import { NoCompanyNotice } from './no-company-notice';

/** Tableau de bord recruteur (version simple ; graphiques et scores à l'étape des dashboards). */
@Component({
  selector: 'app-recruiter-dashboard-page',
  imports: [RouterLink, DatePipe, LoadingSpinner, StatusBadge, NoCompanyNotice],
  template: `
    @if (loading()) {
      <app-loading-spinner />
    } @else if (error()) {
      <div class="alert alert-danger">{{ error() }}</div>
    } @else if (profile(); as p) {
      <div class="d-flex flex-wrap justify-content-between align-items-start gap-2">
        <div>
          <h1 class="page-title">Bonjour {{ p.firstName }}</h1>
          <p class="page-subtitle">
            @if (p.company) {
              Recruteur chez <strong>{{ p.company.name }}</strong>
            } @else {
              Bienvenue dans votre espace recruteur.
            }
          </p>
        </div>
        @if (p.company) {
          <a class="btn btn-primary" routerLink="/recruiter/jobs/create"><i aria-hidden="true" class="bi bi-plus-lg me-1"></i>Nouvelle offre</a>
        }
      </div>

      @if (!p.company) {
        <app-no-company-notice />
      } @else {
        <div class="row g-3 mb-4">
          @for (stat of stats(); track stat.label) {
            <div class="col-6 col-xl-3">
              <div class="card stat-card h-100">
                <div class="card-body">
                  <span class="stat-icon bg-{{ stat.tone }}-subtle text-{{ stat.tone }}-emphasis mb-2"><i aria-hidden="true" class="bi {{ stat.icon }}"></i></span>
                  <div class="stat-value">{{ stat.value }}</div>
                  <div class="stat-label">{{ stat.label }}</div>
                </div>
              </div>
            </div>
          }
        </div>

        <div class="card">
          <div class="card-header bg-white d-flex justify-content-between align-items-center">
            <span class="section-title">Offres récentes</span>
            <a routerLink="/recruiter/jobs" class="small">Toutes mes offres</a>
          </div>
          @if (jobs().length === 0) {
            <div class="empty-state">
              <i aria-hidden="true" class="bi bi-file-earmark-plus"></i>Vous n'avez pas encore créé d'offre.
              <div><a class="btn btn-primary btn-sm mt-2" routerLink="/recruiter/jobs/create">Créer une offre</a></div>
            </div>
          } @else {
            <div class="table-responsive">
              <table class="table align-middle mb-0">
                <thead class="table-light"><tr><th>Offre</th><th>Statut</th><th class="text-center">Candidatures</th><th class="d-none d-md-table-cell">Créée le</th><th></th></tr></thead>
                <tbody>
                  @for (job of jobs().slice(0, 5); track job.id) {
                    <tr>
                      <td class="fw-semibold">{{ job.title }}</td>
                      <td><app-status-badge [status]="job.status" kind="jobStatus" /></td>
                      <td class="text-center">{{ job.applicationCount ?? 0 }}</td>
                      <td class="d-none d-md-table-cell">{{ job.createdAt | date: 'dd/MM/yyyy' }}</td>
                      <td class="text-end"><a class="btn btn-sm btn-outline-primary" [routerLink]="['/recruiter/jobs', job.id, 'candidates']">Candidats</a></td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
        </div>
      }
    }
  `,
})
export class RecruiterDashboardPage implements OnInit {
  private readonly recruiterService = inject(RecruiterService);
  private readonly jobService = inject(JobOfferService);

  readonly profile = signal<RecruiterProfile | null>(null);
  readonly jobs = signal<JobOffer[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  readonly stats = computed(() => {
    const jobs = this.jobs();
    return [
      { label: 'Offres au total', value: jobs.length, icon: 'bi-list-task', tone: 'primary' },
      { label: 'Offres publiées', value: jobs.filter((j) => j.status === 'PUBLIEE').length, icon: 'bi-broadcast', tone: 'success' },
      { label: 'Brouillons', value: jobs.filter((j) => j.status === 'BROUILLON').length, icon: 'bi-pencil-square', tone: 'secondary' },
      { label: 'Candidatures reçues', value: jobs.reduce((sum, j) => sum + (j.applicationCount ?? 0), 0), icon: 'bi-people', tone: 'info' },
    ];
  });

  ngOnInit(): void {
    this.recruiterService
      .getProfile()
      .pipe(
        switchMap((profile) => {
          this.profile.set(profile);
          return profile.company ? this.jobService.findMine() : of([]);
        }),
      )
      .subscribe({
        next: (jobs) => {
          this.jobs.set(jobs);
          this.loading.set(false);
        },
        error: (err) => {
          this.error.set(errorMessage(err, 'Impossible de charger le tableau de bord.'));
          this.loading.set(false);
        },
      });
  }
}
