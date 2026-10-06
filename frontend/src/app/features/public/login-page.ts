import { Component, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { errorMessage } from '../../core/http-errors';
import { FieldError } from '../../shared/field-error';

@Component({
  selector: 'app-login-page',
  imports: [ReactiveFormsModule, RouterLink, FieldError],
  template: `
    <div class="container py-5">
      <div class="row justify-content-center">
        <div class="col-md-7 col-lg-5">
          <div class="card shadow-sm">
            <div class="card-body p-4 p-md-5">
              <h1 class="h3 fw-bold mb-1">Connexion</h1>
              <p class="text-secondary mb-4">Accédez à votre espace SmartRecruit.</p>

              @if (expired()) {
                <div class="alert alert-info py-2"><i aria-hidden="true" class="bi bi-clock-history me-1"></i>Votre session a expiré, reconnectez-vous.</div>
              }
              @if (error()) {
                <div class="alert alert-danger py-2" role="alert" data-testid="login-error">{{ error() }}</div>
              }

              <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
                <div class="mb-3">
                  <label class="form-label" for="email">Email</label>
                  <input id="email" type="email" class="form-control" formControlName="email" autocomplete="email"
                         [class.is-invalid]="form.controls.email.touched && form.controls.email.invalid" />
                  <app-field-error [control]="form.controls.email" />
                </div>
                <div class="mb-4">
                  <label class="form-label" for="password">Mot de passe</label>
                  <input id="password" type="password" class="form-control" formControlName="password"
                         autocomplete="current-password"
                         [class.is-invalid]="form.controls.password.touched && form.controls.password.invalid" />
                  <app-field-error [control]="form.controls.password" />
                </div>
                <button class="btn btn-primary w-100" type="submit" [disabled]="loading()">
                  @if (loading()) {
                    <span class="spinner-border spinner-border-sm me-2"></span>
                  }
                  Se connecter
                </button>
              </form>
              <p class="text-center text-secondary mt-4 mb-0">
                Pas encore de compte ?
                <a routerLink="/register" [queryParams]="returnUrl() ? { returnUrl: returnUrl() } : {}">Créer un compte</a>
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  `,
})
export class LoginPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /** Paramètres de requête liés automatiquement. */
  readonly returnUrl = input<string>();
  readonly expired = input<string>();

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = inject(NonNullableFormBuilder).group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.auth.login(this.form.getRawValue()).subscribe({
      next: (user) => {
        const target = this.returnUrl()?.startsWith('/') ? this.returnUrl()! : this.auth.homeFor(user.role);
        void this.router.navigateByUrl(target);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(errorMessage(err, 'Connexion impossible.'));
      },
    });
  }
}
