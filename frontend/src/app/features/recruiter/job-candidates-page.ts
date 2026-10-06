import { DatePipe } from '@angular/common';
import { Component, computed, inject, input, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { errorMessage } from '../../core/http-errors';
import {
  APPLICATION_STATUS_LABELS,
  ApplicationStatus,
  JobOffer,
  optionsOf,
  RecruiterApplication,
} from '../../core/models';
import { ApplicationService } from '../../core/services/application.service';
import { JobOfferService } from '../../core/services/job-offer.service';
import { ToastService } from '../../core/services/toast.service';
import { LabelPipe } from '../../shared/label.pipe';
import { LoadingSpinner } from '../../shared/loading-spinner';
import { StatusBadge } from '../../shared/status-badge';

/** Candidatures reçues pour une offre du recruteur. */
@Component({
  selector: 'app-job-candidates-page',
  imports: [RouterLink, DatePipe, LabelPipe, LoadingSpinner, StatusBadge],
  templateUrl: './job-candidates-page.html',
})
export class JobCandidatesPage implements OnInit {
  private readonly jobService = inject(JobOfferService);
  private readonly applicationService = inject(ApplicationService);
  private readonly toast = inject(ToastService);

  readonly id = input.required<string>();

  readonly statusOptions = optionsOf(APPLICATION_STATUS_LABELS);
  readonly job = signal<JobOffer | null>(null);
  readonly applications = signal<RecruiterApplication[]>([]);
  readonly filter = signal<ApplicationStatus | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly busyId = signal<number | null>(null);

  readonly requiredSkills = computed(() => new Set((this.job()?.requiredSkills ?? []).map((s) => s.toLowerCase())));
  readonly filtered = computed(() => {
    const status = this.filter();
    return status ? this.applications().filter((a) => a.status === status) : this.applications();
  });

  ngOnInit(): void {
    const id = Number(this.id());
    forkJoin({ job: this.jobService.getMine(id), applications: this.applicationService.findForOffer(id) }).subscribe({
      next: ({ job, applications }) => {
        this.job.set(job);
        this.applications.set(applications);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(errorMessage(err, 'Impossible de charger les candidatures.'));
        this.loading.set(false);
      },
    });
  }

  countOf(status: ApplicationStatus): number {
    return this.applications().filter((a) => a.status === status).length;
  }

  /** Compétence du candidat également demandée par l'offre (simple correspondance de libellé). */
  isRequired(skill: string): boolean {
    return this.requiredSkills().has(skill.toLowerCase());
  }

  changeStatus(application: RecruiterApplication, status: ApplicationStatus): void {
    if (status === application.status) {
      return;
    }
    this.busyId.set(application.id);
    this.applicationService.updateStatus(application.id, status).subscribe({
      next: (updated) => {
        this.applications.update((list) => list.map((a) => (a.id === updated.id ? updated : a)));
        this.busyId.set(null);
        this.toast.success(
          `${updated.candidate.firstName} ${updated.candidate.lastName} : ${APPLICATION_STATUS_LABELS[updated.status].toLowerCase()}.`,
        );
      },
      error: (err) => {
        this.busyId.set(null);
        this.toast.error(errorMessage(err, 'Changement de statut impossible.'));
      },
    });
  }
}
