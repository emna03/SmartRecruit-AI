import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { errorMessage } from '../../core/http-errors';
import { AdminService } from '../../core/services/admin.service';
import { LoadingSpinner } from '../../shared/loading-spinner';

interface Stat {
  label: string;
  value: number;
  icon: string;
  tone: string;
  link: string;
}

@Component({
  selector: 'app-admin-dashboard-page',
  imports: [RouterLink, LoadingSpinner],
  template: `
    <h1 class="page-title">Administration</h1>
    <p class="page-subtitle">Vue d'ensemble de la plateforme.</p>
    @if (loading()) {
      <app-loading-spinner />
    } @else if (error()) {
      <div class="alert alert-danger">{{ error() }}</div>
    } @else {
      <div class="row g-3">
        @for (stat of stats(); track stat.label) {
          <div class="col-6 col-xl-4">
            <a class="card stat-card card-hover h-100 text-decoration-none text-reset" [routerLink]="stat.link">
              <div class="card-body">
                <span class="stat-icon bg-{{ stat.tone }}-subtle text-{{ stat.tone }}-emphasis mb-2"><i aria-hidden="true" class="bi {{ stat.icon }}"></i></span>
                <div class="stat-value">{{ stat.value }}</div>
                <div class="stat-label">{{ stat.label }}</div>
              </div>
            </a>
          </div>
        }
      </div>
    }
  `,
})
export class AdminDashboardPage implements OnInit {
  private readonly adminService = inject(AdminService);

  readonly stats = signal<Stat[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    forkJoin({
      users: this.adminService.findUsers(),
      companies: this.adminService.findCompanies(),
      jobs: this.adminService.findJobs(),
    }).subscribe({
      next: ({ users, companies, jobs }) => {
        const applications = jobs.reduce((sum, j) => sum + (j.applicationCount ?? 0), 0);
        this.stats.set([
          { label: 'Candidats', value: users.filter((u) => u.role === 'CANDIDAT').length, icon: 'bi-person-badge', tone: 'primary', link: '/admin/users' },
          { label: 'Recruteurs', value: users.filter((u) => u.role === 'RECRUTEUR').length, icon: 'bi-briefcase', tone: 'info', link: '/admin/users' },
          { label: 'Entreprises', value: companies.length, icon: 'bi-building', tone: 'secondary', link: '/admin/companies' },
          { label: 'Offres publiées', value: jobs.filter((j) => j.status === 'PUBLIEE').length, icon: 'bi-broadcast', tone: 'success', link: '/admin/jobs' },
          { label: 'Offres au total', value: jobs.length, icon: 'bi-list-task', tone: 'warning', link: '/admin/jobs' },
          { label: 'Candidatures', value: applications, icon: 'bi-send', tone: 'danger', link: '/admin/jobs' },
        ]);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(errorMessage(err, 'Impossible de charger les statistiques.'));
        this.loading.set(false);
      },
    });
  }
}
