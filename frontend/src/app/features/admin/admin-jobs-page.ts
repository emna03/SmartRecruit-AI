import { DatePipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { errorMessage } from '../../core/http-errors';
import { JOB_STATUS_LABELS, JobOffer, JobOfferStatus, optionsOf } from '../../core/models';
import { AdminService } from '../../core/services/admin.service';
import { ToastService } from '../../core/services/toast.service';
import { LoadingSpinner } from '../../shared/loading-spinner';
import { StatusBadge } from '../../shared/status-badge';

@Component({
  selector: 'app-admin-jobs-page',
  imports: [DatePipe, LoadingSpinner, StatusBadge],
  template: `
    <h1 class="page-title">Offres</h1>
    <p class="page-subtitle">Modération des offres de toutes les entreprises.</p>

    <div class="d-flex flex-wrap gap-2 mb-3" role="group" aria-label="Filtrer par statut">
      <button type="button" [class]="status() === null ? 'btn btn-sm btn-primary' : 'btn btn-sm btn-outline-secondary'" (click)="load(null)">Toutes</button>
      @for (o of statusOptions; track o.value) {
        <button type="button" [class]="status() === o.value ? 'btn btn-sm btn-primary' : 'btn btn-sm btn-outline-secondary'" (click)="load(o.value)">{{ o.label }}</button>
      }
    </div>

    @if (loading()) {
      <app-loading-spinner />
    } @else if (error()) {
      <div class="alert alert-danger">{{ error() }}</div>
    } @else {
      <div class="card">
        <div class="table-responsive">
          <table class="table table-hover align-middle mb-0">
            <thead class="table-light"><tr><th>Offre</th><th>Entreprise</th><th>Statut</th><th class="text-center">Candidatures</th><th class="d-none d-lg-table-cell">Créée le</th><th class="text-end">Actions</th></tr></thead>
            <tbody>
              @for (job of jobs(); track job.id) {
                <tr>
                  <td class="fw-semibold">{{ job.title }}</td>
                  <td>{{ job.company.name }}</td>
                  <td><app-status-badge [status]="job.status" kind="jobStatus" /></td>
                  <td class="text-center">{{ job.applicationCount ?? 0 }}</td>
                  <td class="d-none d-lg-table-cell">{{ job.createdAt | date: 'dd/MM/yyyy' }}</td>
                  <td class="text-end text-nowrap">
                    @if (job.status === 'PUBLIEE') {
                      <button class="btn btn-sm btn-outline-dark me-1" type="button" [disabled]="busyId() === job.id" (click)="close(job)">Fermer</button>
                    }
                    <button class="btn btn-sm btn-outline-danger" type="button" [disabled]="busyId() === job.id" (click)="remove(job)" aria-label="Supprimer"><i aria-hidden="true" class="bi bi-trash"></i></button>
                  </td>
                </tr>
              } @empty {
                <tr><td colspan="6" class="text-center text-secondary py-4">Aucune offre.</td></tr>
              }
            </tbody>
          </table>
        </div>
      </div>
    }
  `,
})
export class AdminJobsPage implements OnInit {
  private readonly adminService = inject(AdminService);
  private readonly toast = inject(ToastService);

  readonly statusOptions = optionsOf(JOB_STATUS_LABELS);
  readonly jobs = signal<JobOffer[]>([]);
  readonly status = signal<JobOfferStatus | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly busyId = signal<number | null>(null);

  ngOnInit(): void {
    this.load(null);
  }

  load(status: JobOfferStatus | null): void {
    this.status.set(status);
    this.loading.set(true);
    this.error.set(null);
    this.adminService.findJobs(status).subscribe({
      next: (jobs) => {
        this.jobs.set(jobs);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(errorMessage(err, 'Impossible de charger les offres.'));
        this.loading.set(false);
      },
    });
  }

  close(job: JobOffer): void {
    this.busyId.set(job.id);
    this.adminService.updateJobStatus(job.id, 'FERMEE').subscribe({
      next: (updated) => {
        this.jobs.update((list) => list.map((j) => (j.id === updated.id ? updated : j)));
        this.busyId.set(null);
        this.toast.success('Offre fermée.');
      },
      error: (err) => {
        this.busyId.set(null);
        this.toast.error(errorMessage(err, 'Action impossible.'));
      },
    });
  }

  remove(job: JobOffer): void {
    if (!confirm(`Supprimer l'offre « ${job.title} » et ses candidatures ?`)) {
      return;
    }
    this.busyId.set(job.id);
    this.adminService.deleteJob(job.id).subscribe({
      next: () => {
        this.jobs.update((list) => list.filter((j) => j.id !== job.id));
        this.busyId.set(null);
        this.toast.success('Offre supprimée.');
      },
      error: (err) => {
        this.busyId.set(null);
        this.toast.error(errorMessage(err, 'Suppression impossible.'));
      },
    });
  }
}
