import { DatePipe } from '@angular/common';
import { Component, inject, input, output, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { applyFieldErrors, emptyToNull, errorMessage } from '../../../core/http-errors';
import { CandidateProfile, Education } from '../../../core/models';
import { CandidateProfileService } from '../../../core/services/candidate-profile.service';
import { ToastService } from '../../../core/services/toast.service';
import { FieldError } from '../../../shared/field-error';
import { orderedFields } from '../../../shared/validators';

@Component({
  selector: 'app-education-section',
  imports: [ReactiveFormsModule, DatePipe, FieldError],
  templateUrl: './education-section.html',
})
export class EducationSection {
  private readonly profileService = inject(CandidateProfileService);
  private readonly toast = inject(ToastService);

  readonly profile = input.required<CandidateProfile>();
  readonly changed = output<CandidateProfile>();

  /** null : formulaire fermé ; 'new' : ajout ; nombre : modification de cette formation. */
  readonly editing = signal<number | 'new' | null>(null);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = inject(FormBuilder).nonNullable.group(
    {
      degree: ['', [Validators.required, Validators.maxLength(150)]],
      fieldOfStudy: ['', Validators.maxLength(150)],
      institution: ['', [Validators.required, Validators.maxLength(150)]],
      startDate: [''],
      endDate: [''],
      description: ['', Validators.maxLength(2000)],
    },
    { validators: orderedFields('startDate', 'endDate', 'dateOrder') },
  );

  openNew(): void {
    this.form.reset();
    this.error.set(null);
    this.editing.set('new');
  }

  edit(education: Education): void {
    this.form.reset({
      degree: education.degree,
      fieldOfStudy: education.fieldOfStudy ?? '',
      institution: education.institution,
      startDate: education.startDate ?? '',
      endDate: education.endDate ?? '',
      description: education.description ?? '',
    });
    this.error.set(null);
    this.editing.set(education.id);
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
      degree: v.degree.trim(),
      fieldOfStudy: emptyToNull(v.fieldOfStudy),
      institution: v.institution.trim(),
      startDate: emptyToNull(v.startDate),
      endDate: emptyToNull(v.endDate),
      description: emptyToNull(v.description),
    };
    const editing = this.editing();
    const call =
      editing === 'new' || editing === null
        ? this.profileService.addEducation(request)
        : this.profileService.updateEducation(editing, request);
    this.saving.set(true);
    this.error.set(null);
    call.subscribe({
      next: (profile) => {
        this.saving.set(false);
        this.editing.set(null);
        this.changed.emit(profile);
        this.toast.success('Formation enregistrée.');
      },
      error: (err) => {
        this.saving.set(false);
        applyFieldErrors(this.form, err);
        this.error.set(errorMessage(err, "La formation n'a pas pu être enregistrée."));
      },
    });
  }

  remove(education: Education): void {
    if (!confirm(`Supprimer la formation « ${education.degree} » ?`)) {
      return;
    }
    this.profileService.deleteEducation(education.id).subscribe({
      next: (profile) => {
        this.changed.emit(profile);
        this.toast.success('Formation supprimée.');
      },
      error: (err) => this.toast.error(errorMessage(err, 'Suppression impossible.')),
    });
  }
}
