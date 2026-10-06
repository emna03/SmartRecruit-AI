import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { catchError, forkJoin, of, throwError } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { applyFieldErrors, emptyToNull, errorMessage } from '../../core/http-errors';
import { Company } from '../../core/models';
import { RecruiterService } from '../../core/services/recruiter.service';
import { ToastService } from '../../core/services/toast.service';
import { FieldError } from '../../shared/field-error';
import { LoadingSpinner } from '../../shared/loading-spinner';
import { PHONE_PATTERN, WEBSITE_PATTERN } from '../../shared/validators';

@Component({
  selector: 'app-recruiter-company-page',
  imports: [ReactiveFormsModule, FieldError, LoadingSpinner],
  templateUrl: './recruiter-company-page.html',
})
export class RecruiterCompanyPage implements OnInit {
  private readonly recruiterService = inject(RecruiterService);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);
  private readonly fb = inject(FormBuilder).nonNullable;

  readonly company = signal<Company | null>(null);
  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);
  readonly savingCompany = signal(false);
  readonly companyError = signal<string | null>(null);
  readonly savingProfile = signal(false);
  readonly profileError = signal<string | null>(null);

  readonly companyForm = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(150)]],
    sector: ['', Validators.maxLength(100)],
    location: ['', Validators.maxLength(150)],
    website: ['', [Validators.maxLength(255), Validators.pattern(WEBSITE_PATTERN)]],
    description: ['', Validators.maxLength(5000)],
  });

  readonly profileForm = this.fb.group({
    firstName: ['', [Validators.required, Validators.maxLength(100)]],
    lastName: ['', [Validators.required, Validators.maxLength(100)]],
    jobTitle: ['', Validators.maxLength(100)],
    phone: ['', Validators.pattern(PHONE_PATTERN)],
  });

  ngOnInit(): void {
    forkJoin({
      profile: this.recruiterService.getProfile(),
      company: this.recruiterService.getCompany().pipe(
        catchError((err: unknown) =>
          err instanceof HttpErrorResponse && err.status === 404 ? of(null) : throwError(() => err),
        ),
      ),
    }).subscribe({
      next: ({ profile, company }) => {
        this.profileForm.reset({
          firstName: profile.firstName,
          lastName: profile.lastName,
          jobTitle: profile.jobTitle ?? '',
          phone: profile.phone ?? '',
        });
        this.setCompany(company);
        this.loading.set(false);
      },
      error: (err) => {
        this.loadError.set(errorMessage(err, 'Impossible de charger vos informations.'));
        this.loading.set(false);
      },
    });
  }

  saveCompany(): void {
    if (this.companyForm.invalid) {
      this.companyForm.markAllAsTouched();
      return;
    }
    const v = this.companyForm.getRawValue();
    const request = {
      name: v.name.trim(),
      sector: emptyToNull(v.sector),
      location: emptyToNull(v.location),
      website: emptyToNull(v.website),
      description: emptyToNull(v.description),
    };
    const creating = this.company() === null;
    this.savingCompany.set(true);
    this.companyError.set(null);
    (creating ? this.recruiterService.createCompany(request) : this.recruiterService.updateCompany(request)).subscribe({
      next: (company) => {
        this.setCompany(company);
        this.savingCompany.set(false);
        this.toast.success(creating ? 'Entreprise créée ! Vous pouvez publier des offres.' : 'Entreprise mise à jour.');
      },
      error: (err) => {
        this.savingCompany.set(false);
        applyFieldErrors(this.companyForm, err);
        this.companyError.set(errorMessage(err, "L'entreprise n'a pas pu être enregistrée."));
      },
    });
  }

  saveProfile(): void {
    if (this.profileForm.invalid) {
      this.profileForm.markAllAsTouched();
      return;
    }
    const v = this.profileForm.getRawValue();
    this.savingProfile.set(true);
    this.profileError.set(null);
    this.recruiterService
      .updateProfile({
        firstName: v.firstName.trim(),
        lastName: v.lastName.trim(),
        jobTitle: emptyToNull(v.jobTitle),
        phone: emptyToNull(v.phone),
      })
      .subscribe({
        next: () => {
          this.savingProfile.set(false);
          this.profileForm.markAsPristine();
          this.toast.success('Profil enregistré.');
          this.auth.refreshUser().subscribe({ error: () => undefined });
        },
        error: (err) => {
          this.savingProfile.set(false);
          applyFieldErrors(this.profileForm, err);
          this.profileError.set(errorMessage(err, "Le profil n'a pas pu être enregistré."));
        },
      });
  }

  private setCompany(company: Company | null): void {
    this.company.set(company);
    if (company) {
      this.companyForm.reset({
        name: company.name,
        sector: company.sector ?? '',
        location: company.location ?? '',
        website: company.website ?? '',
        description: company.description ?? '',
      });
    }
  }
}
