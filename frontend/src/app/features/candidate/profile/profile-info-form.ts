import { Component, inject, input, OnInit, output, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../../core/auth/auth.service';
import { applyFieldErrors, emptyToNull, errorMessage } from '../../../core/http-errors';
import { CandidateProfile, EDUCATION_LEVEL_LABELS, EducationLevel, optionsOf } from '../../../core/models';
import { CandidateProfileService } from '../../../core/services/candidate-profile.service';
import { ToastService } from '../../../core/services/toast.service';
import { FieldError } from '../../../shared/field-error';
import { PHONE_PATTERN } from '../../../shared/validators';

@Component({
  selector: 'app-profile-info-form',
  imports: [ReactiveFormsModule, FieldError],
  templateUrl: './profile-info-form.html',
})
export class ProfileInfoForm implements OnInit {
  private readonly profileService = inject(CandidateProfileService);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);

  readonly profile = input.required<CandidateProfile>();
  readonly saved = output<CandidateProfile>();

  readonly educationOptions = optionsOf(EDUCATION_LEVEL_LABELS);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = inject(FormBuilder).nonNullable.group({
    firstName: ['', [Validators.required, Validators.maxLength(100)]],
    lastName: ['', [Validators.required, Validators.maxLength(100)]],
    headline: ['', Validators.maxLength(150)],
    summary: ['', Validators.maxLength(5000)],
    phone: ['', Validators.pattern(PHONE_PATTERN)],
    location: ['', Validators.maxLength(150)],
    yearsOfExperience: [null as number | null, [Validators.min(0), Validators.max(60)]],
    educationLevel: ['' as EducationLevel | ''],
  });

  ngOnInit(): void {
    const p = this.profile();
    this.form.reset({
      firstName: p.firstName,
      lastName: p.lastName,
      headline: p.headline ?? '',
      summary: p.summary ?? '',
      phone: p.phone ?? '',
      location: p.location ?? '',
      yearsOfExperience: p.yearsOfExperience,
      educationLevel: p.educationLevel ?? '',
    });
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.saving.set(true);
    this.error.set(null);
    this.profileService
      .update({
        firstName: v.firstName.trim(),
        lastName: v.lastName.trim(),
        headline: emptyToNull(v.headline),
        summary: emptyToNull(v.summary),
        phone: emptyToNull(v.phone),
        location: emptyToNull(v.location),
        yearsOfExperience: v.yearsOfExperience === null || `${v.yearsOfExperience}` === '' ? null : Number(v.yearsOfExperience),
        educationLevel: v.educationLevel || null,
      })
      .subscribe({
        next: (profile) => {
          this.saving.set(false);
          this.form.markAsPristine();
          this.saved.emit(profile);
          this.toast.success('Profil enregistré.');
          this.auth.refreshUser().subscribe({ error: () => undefined });
        },
        error: (err) => {
          this.saving.set(false);
          applyFieldErrors(this.form, err);
          this.error.set(errorMessage(err, "Le profil n'a pas pu être enregistré."));
        },
      });
  }
}
