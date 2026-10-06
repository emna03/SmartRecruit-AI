import { DatePipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { AuthService } from '../../core/auth/auth.service';
import { errorMessage } from '../../core/http-errors';
import { optionsOf, Role, ROLE_LABELS, User } from '../../core/models';
import { AdminService } from '../../core/services/admin.service';
import { ToastService } from '../../core/services/toast.service';
import { LabelPipe } from '../../shared/label.pipe';
import { LoadingSpinner } from '../../shared/loading-spinner';

@Component({
  selector: 'app-admin-users-page',
  imports: [DatePipe, LabelPipe, LoadingSpinner],
  template: `
    <h1 class="page-title">Utilisateurs</h1>
    <p class="page-subtitle">Activez, désactivez ou supprimez des comptes.</p>

    <div class="d-flex flex-wrap gap-2 mb-3" role="group" aria-label="Filtrer par rôle">
      <button type="button" [class]="role() === null ? 'btn btn-sm btn-primary' : 'btn btn-sm btn-outline-secondary'" (click)="load(null)">Tous</button>
      @for (o of roleOptions; track o.value) {
        <button type="button" [class]="role() === o.value ? 'btn btn-sm btn-primary' : 'btn btn-sm btn-outline-secondary'" (click)="load(o.value)">{{ o.label }}</button>
      }
    </div>

    @if (loading()) {
      <app-loading-spinner />
    } @else if (error()) {
      <div class="alert alert-danger">{{ error() }}</div>
    } @else {
      <div class="card">
        <div class="table-responsive">
          <table class="table table-hover align-middle mb-0">
            <thead class="table-light"><tr><th>Nom</th><th>Email</th><th>Rôle</th><th class="d-none d-md-table-cell">Inscrit le</th><th>État</th><th class="text-end">Actions</th></tr></thead>
            <tbody>
              @for (u of users(); track u.id) {
                <tr>
                  <td class="fw-semibold">{{ u.firstName }} {{ u.lastName }}</td>
                  <td>{{ u.email }}</td>
                  <td>{{ u.role | label: 'role' }}</td>
                  <td class="d-none d-md-table-cell">{{ u.createdAt | date: 'dd/MM/yyyy' }}</td>
                  <td>
                    <span class="badge rounded-pill" [class]="u.enabled ? 'badge rounded-pill text-bg-success' : 'badge rounded-pill text-bg-secondary'">
                      {{ u.enabled ? 'Actif' : 'Désactivé' }}
                    </span>
                  </td>
                  <td class="text-end text-nowrap">
                    @if (u.id !== currentUserId) {
                      <button class="btn btn-sm btn-outline-secondary me-1" type="button" [disabled]="busyId() === u.id" (click)="toggle(u)">
                        {{ u.enabled ? 'Désactiver' : 'Activer' }}
                      </button>
                      <button class="btn btn-sm btn-outline-danger" type="button" [disabled]="busyId() === u.id" (click)="remove(u)" aria-label="Supprimer"><i aria-hidden="true" class="bi bi-trash"></i></button>
                    } @else {
                      <span class="small text-secondary">Vous</span>
                    }
                  </td>
                </tr>
              } @empty {
                <tr><td colspan="6" class="text-center text-secondary py-4">Aucun utilisateur.</td></tr>
              }
            </tbody>
          </table>
        </div>
      </div>
    }
  `,
})
export class AdminUsersPage implements OnInit {
  private readonly adminService = inject(AdminService);
  private readonly toast = inject(ToastService);

  readonly currentUserId = inject(AuthService).user()?.id;
  readonly roleOptions = optionsOf(ROLE_LABELS);
  readonly users = signal<User[]>([]);
  readonly role = signal<Role | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly busyId = signal<number | null>(null);

  ngOnInit(): void {
    this.load(null);
  }

  load(role: Role | null): void {
    this.role.set(role);
    this.loading.set(true);
    this.error.set(null);
    this.adminService.findUsers(role).subscribe({
      next: (users) => {
        this.users.set(users);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(errorMessage(err, 'Impossible de charger les utilisateurs.'));
        this.loading.set(false);
      },
    });
  }

  toggle(user: User): void {
    this.busyId.set(user.id);
    this.adminService.setUserEnabled(user.id, !user.enabled).subscribe({
      next: (updated) => {
        this.users.update((list) => list.map((u) => (u.id === updated.id ? updated : u)));
        this.busyId.set(null);
        this.toast.success(updated.enabled ? 'Compte activé.' : 'Compte désactivé.');
      },
      error: (err) => {
        this.busyId.set(null);
        this.toast.error(errorMessage(err, 'Action impossible.'));
      },
    });
  }

  remove(user: User): void {
    if (!confirm(`Supprimer définitivement le compte de ${user.firstName} ${user.lastName} et toutes ses données ?`)) {
      return;
    }
    this.busyId.set(user.id);
    this.adminService.deleteUser(user.id).subscribe({
      next: () => {
        this.users.update((list) => list.filter((u) => u.id !== user.id));
        this.busyId.set(null);
        this.toast.success('Compte supprimé.');
      },
      error: (err) => {
        this.busyId.set(null);
        this.toast.error(errorMessage(err, 'Suppression impossible.'));
      },
    });
  }
}
