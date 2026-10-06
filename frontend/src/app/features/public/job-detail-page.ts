import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, DestroyRef, inject, input, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { catchError, EMPTY, forkJoin, of, switchMap, tap } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { emptyToNull, errorMessage } from '../../core/http-errors';
import { CandidateApplication, CandidateMatching, JobOffer } from '../../core/models';
import { ApplicationService } from '../../core/services/application.service';
import { JobOfferService } from '../../core/services/job-offer.service';
import { MatchingService } from '../../core/services/matching.service';
import { ToastService } from '../../core/services/toast.service';
import { FieldError } from '../../shared/field-error';
import { LabelPipe } from '../../shared/label.pipe';
import { LoadingSpinner } from '../../shared/loading-spinner';
import { MatchingResultCard } from '../../shared/matching-result-card';
import { StatusBadge } from '../../shared/status-badge';

@Component({
  selector: 'app-job-detail-page',
  imports: [
    RouterLink, ReactiveFormsModule, DatePipe, DecimalPipe, LabelPipe, LoadingSpinner, StatusBadge, FieldError,
    MatchingResultCard,
  ],
  templateUrl: './job-detail-page.html',
})
export class JobDetailPage implements OnInit {
  private readonly jobService = inject(JobOfferService);
  private readonly applicationService = inject(ApplicationService);
  private readonly matchingService = inject(MatchingService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly auth = inject(AuthService);

  /** Paramètre de route :id. */
  readonly id = input.required<string>();
  /** Préfixe de la liste d'origine (/jobs ou /candidate/jobs). */
  readonly detailBase = input('/jobs', { transform: (v: string | undefined) => v ?? '/jobs' });

  readonly job = signal<JobOffer | null>(null);
  readonly myApplication = signal<CandidateApplication | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly applying = signal(false);
  readonly applyError = signal<string | null>(null);
  readonly showApplyForm = signal(false);
  readonly matching = signal<CandidateMatching | null>(null);
  readonly matchingLoading = signal(false);
  readonly matchingError = signal<string | null>(null);

  readonly isCandidate = computed(() => this.auth.role() === 'CANDIDAT');
  readonly coverLetter = new FormControl('', { nonNullable: true, validators: Validators.maxLength(5000) });

  private readonly id$ = toObservable(this.id);

  ngOnInit(): void {
    this.id$
      .pipe(
        tap(() => {
          this.loading.set(true);
          this.error.set(null);
          this.showApplyForm.set(false);
        }),
        switchMap((id) =>
          forkJoin({
            job: this.jobService.getPublished(Number(id)),
            applications: this.isCandidate() ? this.applicationService.findMine() : of([] as CandidateApplication[]),
          }).pipe(
            catchError((err: unknown) => {
              this.error.set(
                err instanceof HttpErrorResponse && err.status === 404
                  ? "Cette offre n'existe pas ou n'est plus disponible."
                  : errorMessage(err, "Impossible de charger l'offre."),
              );
              this.loading.set(false);
              return EMPTY;
            }),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(({ job, applications }) => {
        this.job.set(job);
        this.myApplication.set(applications.find((a) => a.job.id === job.id) ?? null);
        this.loading.set(false);
        if (this.isCandidate()) {
          this.loadMatching(job.id);
        }
      });
  }

  /** Score de compatibilité du candidat avec l'offre (recalculé à partir de son CV analysé). */
  loadMatching(jobId: number): void {
    this.matching.set(null);
    this.matchingError.set(null);
    this.matchingLoading.set(true);
    this.matchingService
      .getForOffer(jobId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (matching) => {
          this.matching.set(matching);
          this.matchingLoading.set(false);
        },
        error: (err) => {
          this.matchingError.set(errorMessage(err, 'Impossible de calculer votre compatibilité avec cette offre.'));
          this.matchingLoading.set(false);
        },
      });
  }

  loginToApply(): void {
    void this.router.navigate(['/login'], { queryParams: { returnUrl: this.router.url } });
  }

  apply(): void {
    const job = this.job();
    if (!job || this.coverLetter.invalid) {
      this.coverLetter.markAsTouched();
      return;
    }
    this.applying.set(true);
    this.applyError.set(null);
    this.applicationService.apply(job.id, emptyToNull(this.coverLetter.value)).subscribe({
      next: (application) => {
        this.myApplication.set(application);
        this.applying.set(false);
        this.showApplyForm.set(false);
        this.toast.success('Candidature envoyée !');
      },
      error: (err) => {
        this.applying.set(false);
        this.applyError.set(errorMessage(err, "L'envoi de la candidature a échoué."));
      },
    });
  }
}
