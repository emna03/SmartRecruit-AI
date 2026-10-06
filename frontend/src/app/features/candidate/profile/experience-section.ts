import { DatePipe } from '@angular/common';
import { Component, inject, input, output, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { applyFieldErrors, emptyToNull, errorMessage } from '../../../core/http-errors';
import { CandidateProfile, Experience } from '../../../core/models';
import { CandidateProfileService } from '../../../core/services/candidate-profile.service';
import { ToastService } from '../../../core/services/toast.service';
import { FieldError } from '../../../shared/field-error';
import { notInFuture, orderedFields, todayIso } from '../../../shared/validators';

@Component({
  selector: 'app-experience-section',
  imports: [ReactiveFormsModule, DatePipe, FieldError],
  templateUrl: './experience-section.html',
})
export class ExperienceSection {
  private readonly profileService = inject(CandidateProfileService);
  private readonly toast = inject(ToastService);

  readonly profile = input.required<CandidateProfile>();
  readonly changed = output<CandidateProfile>();

  readonly today = todayIso();
  readonly editing = signal<number | 'new' | null>(null);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = inject(FormBuilder).nonNullable.group(
    {
      jobTitle: ['', [Validators.required, Validators.maxLength(150)]],
      companyName: ['', [Validators.required, Validators.maxLength(150)]],
      location: ['', Validators.maxLength(150)],
      startDate: ['', [Validators.required, notInFuture]],
      current: [false],
      endDate: [''],
      description: ['', Validators.maxLength(2000)],
    },
    { validators: orderedFields('startDate', 'endDate', 'dateOrder') },
  );

  constructor() {
    this.form.controls.current.valueChanges.subscribe((current) => {
      const endDate = this.form.controls.endDate;
      if (current) {
        endDate.setValue('');
        endDate.disable();
      } else {
        endDate.enable();
      }
    });
  }

  openNew(): void {
    this.form.reset();
    this.error.set(null);
    this.editing.set('new');
  }

  edit(experience: Experience): void {
    this.form.reset({
      jobTitle: experience.jobTitle,
      companyName: experience.companyName,
      location: experience.location ?? '',
      startDate: experience.startDate,
      current: experience.current,
      endDate: experience.endDate ?? '',
      description: experience.description ?? '',
    });
    this.error.set(null);
    this.editing.set(experience.id);
  }

  cancel(): void {
    this.editing.set(null);
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const request = {
      jobTitle: v.jobTitle.trim(),
      companyName: v.companyName.trim(),
      location: emptyToNull(v.location),
      startDate: v.startDate,
      endDate: v.current ? null : emptyToNull(v.endDate),
      description: emptyToNull(v.description),
    };
    const editing = this.editing();
    const call =
      editing === 'new' || editing === null
        ? this.profileService.addExperience(request)
        : this.profileService.updateExperience(editing, request);
    this.saving.set(true);
    this.error.set(null);
    call.subscribe({
      next: (profile) => {
        this.saving.set(false);
        this.editing.set(null);
        this.changed.emit(profile);
        this.toast.success('Expérience enregistrée.');
      },
      error: (err) => {
        this.saving.set(false);
        applyFieldErrors(this.form, err);
        this.error.set(errorMessage(err, "L'expérience n'a pas pu être enregistrée."));
      },
    });
  }

  remove(experience: Experience): void {
    if (!confirm(`Supprimer l'expérience « ${experience.jobTitle} » ?`)) {
      return;
    }
    this.profileService.deleteExperience(experience.id).subscribe({
      next: (profile) => {
        this.changed.emit(profile);
        this.toast.success('Expérience supprimée.');
      },
      error: (err) => this.toast.error(errorMessage(err, 'Suppression impossible.')),
    });
  }
}
