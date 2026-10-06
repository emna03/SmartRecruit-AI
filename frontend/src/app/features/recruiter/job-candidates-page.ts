import { DatePipe, DecimalPipe } from '@angular/common';
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
import { MatchingService } from '../../core/services/matching.service';
import { ToastService } from '../../core/services/toast.service';
import { LabelPipe } from '../../shared/label.pipe';
import { LoadingSpinner } from '../../shared/loading-spinner';
import { MatchScoreBadge } from '../../shared/match-score-badge';
import { StatusBadge } from '../../shared/status-badge';

/** Candidatures reçues pour une offre du recruteur, classées par score de compatibilité (aide à la décision). */
@Component({
  selector: 'app-job-candidates-page',
  imports: [RouterLink, DatePipe, DecimalPipe, LabelPipe, LoadingSpinner, StatusBadge, MatchScoreBadge],
  templateUrl: './job-candidates-page.html',
})
export class JobCandidatesPage implements OnInit {
  private readonly jobService = inject(JobOfferService);
  private readonly applicationService = inject(ApplicationService);
  private readonly matchingService = inject(MatchingService);
  private readonly toast = inject(ToastService);

  readonly id = input.required<string>();

  readonly statusOptions = optionsOf(APPLICATION_STATUS_LABELS);
  readonly job = signal<JobOffer | null>(null);
  readonly applications = signal<RecruiterApplication[]>([]);
  readonly filter = signal<ApplicationStatus | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly busyId = signal<number | null>(null);
  readonly refreshing = signal(false);

  readonly requiredSkills = computed(() => new Set((this.job()?.requiredSkills ?? []).map((s) => s.toLowerCase())));
  readonly filtered = computed(() => {
    const status = this.filter();
    return status ? this.applications().filter((a) => a.status === status) : this.applications();
  });
  /** Rang dans le classement complet (le serveur renvoie les candidatures triées par score décroissant). */
  readonly ranks = computed(() => {
    const ranks = new Map<number, number>();
    this.applications()
      .filter((a) => a.matching)
      .forEach((a, index) => ranks.set(a.id, index + 1));
    return ranks;
  });
  readonly scoredCount = computed(() => this.ranks().size);
  readonly staleCount = computed(() => this.applications().filter((a) => a.matching?.stale).length);

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

  /** Raison de l'absence de score, affichée à la place du score. */
  noScoreReason(a: RecruiterApplication): string {
    if (!a.candidate.hasCv) {
      return 'Aucun CV importé';
    }
    return a.candidate.cvAnalyzed ? 'Score à calculer' : 'CV non analysé';
  }

  /** Recalcule les scores de tous les candidats ayant un CV analysé (service IA). */
  refreshScores(): void {
    const job = this.job();
    if (!job) {
      return;
    }
    this.refreshing.set(true);
    this.matchingService.refreshForOffer(job.id).subscribe({
      next: (result) => {
        this.applications.set(result.applications);
        this.refreshing.set(false);
        const skipped = result.withoutAnalyzedCv ? ` ${result.withoutAnalyzedCv} candidat(s) sans CV analysé.` : '';
        this.toast.success(`${result.computed} score(s) recalculé(s).${skipped}`);
      },
      error: (err) => {
        this.refreshing.set(false);
        this.toast.error(errorMessage(err, 'Le recalcul des scores a échoué.'));
      },
    });
  }

  changeStatus(application: RecruiterApplication, status: ApplicationStatus): void {
    if (status === application.status) {
      return;
    }
    this.busyId.set(application.id);
    this.applicationService.updateStatus(application.id, status).subscribe({
      next: (updated) => {
        this.applications.update((list) => list.map((a) => (a.id === updated.id ? { ...a, status: updated.status } : a)));
        this.busyId.set(null);
        this.toast.success(
          `${application.candidate.firstName} ${application.candidate.lastName} : ${APPLICATION_STATUS_LABELS[updated.status].toLowerCase()}.`,
        );
      },
      error: (err) => {
        this.busyId.set(null);
        this.toast.error(errorMessage(err, 'Changement de statut impossible.'));
      },
    });
  }
}
