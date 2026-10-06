import { DatePipe } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { of, switchMap } from 'rxjs';
import { errorMessage } from '../../core/http-errors';
import { JOB_STATUS_LABELS, JobOffer, JobOfferStatus, optionsOf } from '../../core/models';
import { JobOfferService } from '../../core/services/job-offer.service';
import { RecruiterService } from '../../core/services/recruiter.service';
import { ToastService } from '../../core/services/toast.service';
import { LabelPipe } from '../../shared/label.pipe';
import { LoadingSpinner } from '../../shared/loading-spinner';
import { StatusBadge } from '../../shared/status-badge';
import { NoCompanyNotice } from './no-company-notice';

@Component({
  selector: 'app-recruiter-jobs-page',
  imports: [RouterLink, DatePipe, LabelPipe, LoadingSpinner, StatusBadge, NoCompanyNotice],
  templateUrl: './recruiter-jobs-page.html',
})
export class RecruiterJobsPage implements OnInit {
  private readonly jobService = inject(JobOfferService);
  private readonly recruiterService = inject(RecruiterService);
  private readonly toast = inject(ToastService);

  readonly statusOptions = optionsOf(JOB_STATUS_LABELS);
  readonly jobs = signal<JobOffer[]>([]);
  readonly hasCompany = signal(true);
  readonly filter = signal<JobOfferStatus | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly busyId = signal<number | null>(null);

  readonly filtered = computed(() => {
    const status = this.filter();
    return status ? this.jobs().filter((j) => j.status === status) : this.jobs();
  });

  ngOnInit(): void {
    this.recruiterService
      .getProfile()
      .pipe(
        switchMap((profile) => {
          this.hasCompany.set(profile.company !== null);
          return profile.company ? this.jobService.findMine() : of([]);
        }),
      )
      .subscribe({
        next: (jobs) => {
          this.jobs.set(jobs);
          this.loading.set(false);
        },
        error: (err) => {
          this.error.set(errorMessage(err, 'Impossible de charger vos offres.'));
          this.loading.set(false);
        },
      });
  }

  countOf(status: JobOfferStatus): number {
    return this.jobs().filter((j) => j.status === status).length;
  }

  changeStatus(job: JobOffer, status: JobOfferStatus): void {
    this.busyId.set(job.id);
    this.jobService.updateStatus(job.id, status).subscribe({
      next: (updated) => {
        this.jobs.update((list) => list.map((j) => (j.id === updated.id ? updated : j)));
        this.busyId.set(null);
        this.toast.success(`Offre « ${updated.title} » : ${JOB_STATUS_LABELS[updated.status].toLowerCase()}.`);
      },
      error: (err) => {
        this.busyId.set(null);
        this.toast.error(errorMessage(err, 'Changement de statut impossible.'));
      },
    });
  }

  remove(job: JobOffer): void {
    const warning = job.applicationCount ? ` Ses ${job.applicationCount} candidature(s) seront aussi supprimées.` : '';
    if (!confirm(`Supprimer définitivement l'offre « ${job.title} » ?${warning}`)) {
      return;
    }
    this.busyId.set(job.id);
    this.jobService.delete(job.id).subscribe({
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
