import { Component, inject, input, OnInit, signal } from '@angular/core';
import {
  AbstractControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { applyFieldErrors, errorMessage } from '../../core/http-errors';
import { RegisterRequest } from '../../core/models';
import { ToastService } from '../../core/services/toast.service';
import { FieldError } from '../../shared/field-error';

/** Même règle que le backend : au moins une lettre et un chiffre. */
export const PASSWORD_PATTERN = /^(?=.*[A-Za-z])(?=.*\d).+$/;

export function passwordsMatch(group: AbstractControl): ValidationErrors | null {
  const password = group.get('password')?.value;
  const confirm = group.get('confirmPassword');
  if (!confirm || !confirm.value || password === confirm.value) {
    if (confirm?.hasError('mismatch')) {
      confirm.setErrors(null);
    }
    return null;
  }
  confirm.setErrors({ ...confirm.errors, mismatch: true });
  return { mismatch: true };
}

@Component({
  selector: 'app-register-page',
  imports: [ReactiveFormsModule, RouterLink, FieldError],
  templateUrl: './register-page.html',
  styles: `
    .role-option {
      cursor: pointer;
      border: 2px solid var(--sr-border);
      transition: border-color 0.15s ease, background 0.15s ease;
    }
    .role-option.selected {
      border-color: var(--sr-primary);
      background: var(--sr-primary-soft);
    }
  `,
})
export class RegisterPage implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  readonly role = input<string>();
  readonly returnUrl = input<string>();

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = inject(NonNullableFormBuilder).group(
    {
      role: ['CANDIDAT' as RegisterRequest['role'], Validators.required],
      firstName: ['', [Validators.required, Validators.maxLength(100)]],
      lastName: ['', [Validators.required, Validators.maxLength(100)]],
      email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
      password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72), Validators.pattern(PASSWORD_PATTERN)]],
      confirmPassword: ['', Validators.required],
    },
    { validators: passwordsMatch },
  );

  ngOnInit(): void {
    if (this.role() === 'RECRUTEUR') {
      this.form.controls.role.setValue('RECRUTEUR');
    }
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    const { confirmPassword: _, ...request } = this.form.getRawValue();
    this.auth.register({ ...request, email: request.email.trim() }).subscribe({
      next: (user) => {
        if (this.returnUrl()?.startsWith('/')) {
          void this.router.navigateByUrl(this.returnUrl()!);
        } else if (user.role === 'RECRUTEUR') {
          this.toast.success('Compte créé ! Commencez par renseigner votre entreprise.');
          void this.router.navigateByUrl('/recruiter/company');
        } else {
          this.toast.success('Compte créé ! Complétez votre profil pour postuler.');
          void this.router.navigateByUrl('/candidate/profile');
        }
      },
      error: (err) => {
        this.loading.set(false);
        applyFieldErrors(this.form, err);
        this.error.set(errorMessage(err, 'Inscription impossible.'));
      },
    });
  }
}
