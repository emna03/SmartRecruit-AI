import { Component, DestroyRef, inject, input, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, ParamMap, Router } from '@angular/router';
import { catchError, EMPTY, switchMap, tap } from 'rxjs';
import { errorMessage } from '../../core/http-errors';
import {
  CONTRACT_TYPE_LABELS,
  ContractType,
  EDUCATION_LEVEL_LABELS,
  EducationLevel,
  JobOffer,
  JobSearchCriteria,
  optionsOf,
} from '../../core/models';
import { JobOfferService } from '../../core/services/job-offer.service';
import { JobCard } from '../../shared/job-card';
import { LoadingSpinner } from '../../shared/loading-spinner';
import { Pagination } from '../../shared/pagination';

const PAGE_SIZE = 9;
const FILTER_KEYS = ['keyword', 'location', 'contractType', 'skill', 'educationLevel', 'maxExperienceYears'] as const;

/**
 * Liste publique des offres publiées. Les filtres et la page sont portés par l'URL (?keyword=...&page=1),
 * ce qui rend une recherche partageable et compatible avec le bouton « retour ».
 * Réutilisée dans l'espace candidat (/candidate/jobs) avec un autre préfixe de lien.
 */
@Component({
  selector: 'app-job-list-page',
  imports: [ReactiveFormsModule, JobCard, LoadingSpinner, Pagination],
  templateUrl: './job-list-page.html',
})
export class JobListPage implements OnInit {
  private readonly jobService = inject(JobOfferService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  /** Préfixe des liens de détail (donnée de route). */
  readonly detailBase = input('/jobs', { transform: (v: string | undefined) => v ?? '/jobs' });
  /** Affichage dans un espace connecté (sans marges de page). */
  readonly embedded = input(false, { transform: (v: boolean | undefined) => v ?? false });

  readonly contractOptions = optionsOf(CONTRACT_TYPE_LABELS);
  readonly educationOptions = optionsOf(EDUCATION_LEVEL_LABELS);

  readonly jobs = signal<JobOffer[]>([]);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly showFilters = signal(false);

  readonly form = inject(FormBuilder).group({
    keyword: [''],
    location: [''],
    contractType: ['' as ContractType | ''],
    skill: [''],
    educationLevel: ['' as EducationLevel | ''],
    maxExperienceYears: [null as number | null],
  });

  ngOnInit(): void {
    this.route.queryParamMap
      .pipe(
        tap((params) => this.patchForm(params)),
        tap(() => {
          this.loading.set(true);
          this.error.set(null);
        }),
        switchMap((params) =>
          this.jobService.search(this.criteriaFrom(params)).pipe(
            catchError((err) => {
              this.error.set(errorMessage(err, 'Impossible de charger les offres.'));
              this.loading.set(false);
              return EMPTY;
            }),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((result) => {
        this.jobs.set(result.content);
        this.page.set(result.page);
        this.totalPages.set(result.totalPages);
        this.totalElements.set(result.totalElements);
        this.loading.set(false);
      });
  }

  search(): void {
    this.navigate(0);
  }

  reset(): void {
    this.form.reset({ keyword: '', location: '', contractType: '', skill: '', educationLevel: '', maxExperienceYears: null });
    this.navigate(0);
  }

  goToPage(page: number): void {
    this.navigate(page);
  }

  private navigate(page: number): void {
    const value = this.form.getRawValue();
    const queryParams: Record<string, string | number | null> = {};
    for (const key of FILTER_KEYS) {
      const v = value[key];
      queryParams[key] = v === null || `${v}`.trim() === '' ? null : `${v}`.trim();
    }
    queryParams['page'] = page > 0 ? page : null;
    void this.router.navigate([], { relativeTo: this.route, queryParams });
  }

  private patchForm(params: ParamMap): void {
    const years = params.get('maxExperienceYears');
    this.form.setValue(
      {
        keyword: params.get('keyword') ?? '',
        location: params.get('location') ?? '',
        contractType: (params.get('contractType') as ContractType) ?? '',
        skill: params.get('skill') ?? '',
        educationLevel: (params.get('educationLevel') as EducationLevel) ?? '',
        maxExperienceYears: years !== null && years !== '' ? Number(years) : null,
      },
      { emitEvent: false },
    );
  }

  private criteriaFrom(params: ParamMap): JobSearchCriteria {
    const criteria: JobSearchCriteria = { page: Number(params.get('page') ?? 0) || 0, size: PAGE_SIZE };
    for (const key of FILTER_KEYS) {
      const value = params.get(key);
      if (value) {
        (criteria as Record<string, unknown>)[key] = value;
      }
    }
    return criteria;
  }
}
