import { DatePipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { errorMessage } from '../../core/http-errors';
import { Company } from '../../core/models';
import { AdminService } from '../../core/services/admin.service';
import { ToastService } from '../../core/services/toast.service';
import { LoadingSpinner } from '../../shared/loading-spinner';

@Component({
  selector: 'app-admin-companies-page',
  imports: [DatePipe, LoadingSpinner],
  template: `
    <h1 class="page-title">Entreprises</h1>
    <p class="page-subtitle">Supprimer une entreprise supprime aussi ses offres et leurs candidatures.</p>
    @if (loading()) {
      <app-loading-spinner />
    } @else if (error()) {
      <div class="alert alert-danger">{{ error() }}</div>
    } @else {
      <div class="card">
        <div class="table-responsive">
          <table class="table table-hover align-middle mb-0">
            <thead class="table-light"><tr><th>Nom</th><th class="d-none d-md-table-cell">Secteur</th><th class="d-none d-md-table-cell">Ville</th><th class="text-center">Offres</th><th class="text-center">Recruteurs</th><th class="d-none d-lg-table-cell">Créée le</th><th></th></tr></thead>
            <tbody>
              @for (c of companies(); track c.id) {
                <tr>
                  <td class="fw-semibold">{{ c.name }}</td>
                  <td class="d-none d-md-table-cell">{{ c.sector || '—' }}</td>
                  <td class="d-none d-md-table-cell">{{ c.location || '—' }}</td>
                  <td class="text-center">{{ c.jobOfferCount }}</td>
                  <td class="text-center">{{ c.recruiterCount }}</td>
                  <td class="d-none d-lg-table-cell">{{ c.createdAt | date: 'dd/MM/yyyy' }}</td>
                  <td class="text-end">
                    <button class="btn btn-sm btn-outline-danger" type="button" [disabled]="busyId() === c.id" (click)="remove(c)" aria-label="Supprimer"><i aria-hidden="true" class="bi bi-trash"></i></button>
                  </td>
                </tr>
              } @empty {
                <tr><td colspan="7" class="text-center text-secondary py-4">Aucune entreprise.</td></tr>
              }
            </tbody>
          </table>
        </div>
      </div>
    }
  `,
})
export class AdminCompaniesPage implements OnInit {
  private readonly adminService = inject(AdminService);
  private readonly toast = inject(ToastService);

  readonly companies = signal<Company[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly busyId = signal<number | null>(null);

  ngOnInit(): void {
    this.adminService.findCompanies().subscribe({
      next: (companies) => {
        this.companies.set(companies);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(errorMessage(err, 'Impossible de charger les entreprises.'));
        this.loading.set(false);
      },
    });
  }

  remove(company: Company): void {
    if (!confirm(`Supprimer « ${company.name} », ses ${company.jobOfferCount} offre(s) et leurs candidatures ?`)) {
      return;
    }
    this.busyId.set(company.id);
    this.adminService.deleteCompany(company.id).subscribe({
      next: () => {
        this.companies.update((list) => list.filter((c) => c.id !== company.id));
        this.busyId.set(null);
        this.toast.success('Entreprise supprimée.');
      },
      error: (err) => {
        this.busyId.set(null);
        this.toast.error(errorMessage(err, 'Suppression impossible.'));
      },
    });
  }
}
