import { Component, computed, inject, input, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { applyFieldErrors, emptyToNull, errorMessage } from '../../core/http-errors';
import {
  CONTRACT_TYPE_LABELS,
  ContractType,
  EDUCATION_LEVEL_LABELS,
  EducationLevel,
  JobOfferRequest,
  JobOfferStatus,
  optionsOf,
} from '../../core/models';
import { JobOfferService } from '../../core/services/job-offer.service';
import { ToastService } from '../../core/services/toast.service';
import { FieldError } from '../../shared/field-error';
import { LoadingSpinner } from '../../shared/loading-spinner';
import { SkillsInput } from '../../shared/skills-input';
import { orderedFields } from '../../shared/validators';

function toNumberOrNull(value: number | string | null): number | null {
  return value === null || `${value}`.trim() === '' ? null : Number(value);
}

/** Création (/recruiter/jobs/create) et modification (/recruiter/jobs/:id/edit) d'une offre. */
@Component({
  selector: 'app-job-form-page',
  imports: [ReactiveFormsModule, RouterLink, FieldError, LoadingSpinner, SkillsInput],
  templateUrl: './job-form-page.html',
})
export class JobFormPage implements OnInit {
  private readonly jobService = inject(JobOfferService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  /** Paramètre :id (absent en création). */
  readonly id = input<string>();
  readonly isEdit = computed(() => !!this.id());

  readonly contractOptions = optionsOf(CONTRACT_TYPE_LABELS);
  readonly educationOptions = optionsOf(EDUCATION_LEVEL_LABELS);
  readonly currentStatus = signal<JobOfferStatus | null>(null);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = inject(FormBuilder).nonNullable.group(
    {
      title: ['', [Validators.required, Validators.maxLength(150)]],
      description: ['', [Validators.required, Validators.maxLength(10000)]],
      location: ['', Validators.maxLength(150)],
      contractType: ['' as ContractType | '', Validators.required],
      minExperienceYears: [0 as number | null, [Validators.required, Validators.min(0), Validators.max(50)]],
      educationLevel: ['' as EducationLevel | ''],
      salaryMin: [null as number | null, [Validators.min(0), Validators.max(99999999)]],
      salaryMax: [null as number | null, [Validators.min(0), Validators.max(99999999)]],
      requiredSkills: [[] as string[], [Validators.required, Validators.maxLength(30)]],
    },
    { validators: orderedFields('salaryMin', 'salaryMax', 'salaryOrder') },
  );

  ngOnInit(): void {
    const id = this.id();
    if (!id) {
      return;
    }
    this.loading.set(true);
    this.jobService.getMine(Number(id)).subscribe({
      next: (job) => {
        this.form.reset({
          title: job.title,
          description: job.description,
          location: job.location ?? '',
          contractType: job.contractType,
          minExperienceYears: job.minExperienceYears,
          educationLevel: job.educationLevel ?? '',
          salaryMin: job.salaryMin,
          salaryMax: job.salaryMax,
          requiredSkills: job.requiredSkills,
        });
        this.currentStatus.set(job.status);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(errorMessage(err, "Impossible de charger l'offre."));
        this.loading.set(false);
      },
    });
  }

  /** En création : status = BROUILLON ou PUBLIEE ; en modification : le statut actuel est conservé. */
  submit(status?: JobOfferStatus): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const request: JobOfferRequest = {
      title: v.title.trim(),
      description: v.description.trim(),
      location: emptyToNull(v.location),
      contractType: v.contractType as ContractType,
      minExperienceYears: Number(v.minExperienceYears),
      educationLevel: v.educationLevel || null,
      salaryMin: toNumberOrNull(v.salaryMin),
      salaryMax: toNumberOrNull(v.salaryMax),
      requiredSkills: v.requiredSkills,
      ...(status ? { status } : {}),
    };
    const id = this.id();
    this.saving.set(true);
    this.error.set(null);
    (id ? this.jobService.update(Number(id), request) : this.jobService.create(request)).subscribe({
      next: (job) => {
        this.toast.success(
          id ? 'Offre mise à jour.' : job.status === 'PUBLIEE' ? 'Offre publiée !' : 'Offre enregistrée en brouillon.',
        );
        void this.router.navigateByUrl('/recruiter/jobs');
      },
      error: (err) => {
        this.saving.set(false);
        applyFieldErrors(this.form, err);
        this.error.set(errorMessage(err, "L'offre n'a pas pu être enregistrée."));
      },
    });
  }
}
