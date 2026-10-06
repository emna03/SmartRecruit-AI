import { DatePipe } from '@angular/common';
import { Component, inject, input, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { errorMessage } from '../../core/http-errors';
import { APPLICATION_STATUS_LABELS, ApplicationDetail, ApplicationStatus, optionsOf } from '../../core/models';
import { ApplicationService } from '../../core/services/application.service';
import { ToastService } from '../../core/services/toast.service';
import { LabelPipe } from '../../shared/label.pipe';
import { LoadingSpinner } from '../../shared/loading-spinner';
import { StatusBadge } from '../../shared/status-badge';

@Component({
  selector: 'app-application-detail-page',
  imports: [RouterLink, DatePipe, LabelPipe, LoadingSpinner, StatusBadge],
  templateUrl: './application-detail-page.html',
})
export class ApplicationDetailPage implements OnInit {
  private readonly applicationService = inject(ApplicationService);
  private readonly toast = inject(ToastService);

  readonly id = input.required<string>();

  readonly statusOptions = optionsOf(APPLICATION_STATUS_LABELS);
  readonly application = signal<ApplicationDetail | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly saving = signal(false);

  ngOnInit(): void {
    this.applicationService.getForRecruiter(Number(this.id())).subscribe({
      next: (application) => {
        this.application.set(application);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(errorMessage(err, 'Impossible de charger la candidature.'));
        this.loading.set(false);
      },
    });
  }

  changeStatus(status: ApplicationStatus): void {
    const current = this.application();
    if (!current || current.status === status) {
      return;
    }
    this.saving.set(true);
    this.applicationService.updateStatus(current.id, status).subscribe({
      next: (updated) => {
        this.application.set({ ...current, status: updated.status });
        this.saving.set(false);
        this.toast.success(`Statut mis à jour : ${APPLICATION_STATUS_LABELS[updated.status]}.`);
      },
      error: (err) => {
        this.saving.set(false);
        this.toast.error(errorMessage(err, 'Changement de statut impossible.'));
      },
    });
  }
}
