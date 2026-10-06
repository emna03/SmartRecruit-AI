import { Component, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../core/auth/auth.service';
import { LabelPipe } from '../shared/label.pipe';

@Component({
  selector: 'app-navbar',
  imports: [RouterLink, RouterLinkActive, LabelPipe],
  template: `
    <nav class="navbar navbar-expand-lg bg-white border-bottom sticky-top">
      <div class="container-xl">
        <a class="navbar-brand fw-bold d-flex align-items-center gap-2" routerLink="/" (click)="open.set(false)">
          <span class="brand-logo"><i aria-hidden="true" class="bi bi-stars"></i></span>
          SmartRecruit <span class="text-primary">AI</span>
        </a>
        <button class="navbar-toggler" type="button" aria-label="Menu" [attr.aria-expanded]="open()"
                (click)="open.set(!open())">
          <span class="navbar-toggler-icon"></span>
        </button>
        <div class="collapse navbar-collapse" [class.show]="open()">
          <ul class="navbar-nav me-auto">
            <li class="nav-item">
              <a class="nav-link" routerLink="/jobs" routerLinkActive="active" (click)="open.set(false)">
                <i aria-hidden="true" class="bi bi-search me-1"></i>Offres d'emploi
              </a>
            </li>
            @if (auth.user()) {
              <li class="nav-item">
                <a class="nav-link" [routerLink]="auth.homeFor()" (click)="open.set(false)">
                  <i aria-hidden="true" class="bi bi-grid me-1"></i>Mon espace
                </a>
              </li>
            }
          </ul>
          @if (auth.user(); as user) {
            <div class="d-flex align-items-center gap-3 py-2 py-lg-0">
              <div class="text-end lh-sm">
                <div class="fw-semibold small" data-testid="navbar-user">{{ user.firstName }} {{ user.lastName }}</div>
                <div class="text-secondary" style="font-size: .75rem">{{ user.role | label: 'role' }}</div>
              </div>
              <button class="btn btn-outline-secondary btn-sm" type="button" (click)="logout()">
                <i aria-hidden="true" class="bi bi-box-arrow-right me-1"></i>Déconnexion
              </button>
            </div>
          } @else {
            <div class="d-flex gap-2 py-2 py-lg-0">
              <a class="btn btn-outline-primary btn-sm" routerLink="/login" (click)="open.set(false)">Connexion</a>
              <a class="btn btn-primary btn-sm" routerLink="/register" (click)="open.set(false)">Inscription</a>
            </div>
          }
        </div>
      </div>
    </nav>
  `,
  styles: `
    .brand-logo {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 2rem;
      height: 2rem;
      border-radius: 0.6rem;
      color: #fff;
      background: linear-gradient(135deg, #4f46e5, #7c3aed);
    }
  `,
})
export class Navbar {
  protected readonly auth = inject(AuthService);
  protected readonly open = signal(false);

  logout(): void {
    this.open.set(false);
    this.auth.logout();
  }
}
