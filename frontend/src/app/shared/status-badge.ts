import { Component, computed, input } from '@angular/core';
import { ApplicationStatus, JobOfferStatus } from '../core/models';
import { LabelPipe } from './label.pipe';

const CLASSES: Record<ApplicationStatus | JobOfferStatus, string> = {
  EN_ATTENTE: 'text-bg-warning',
  PRESELECTIONNE: 'text-bg-info',
  ACCEPTE: 'text-bg-success',
  REFUSE: 'text-bg-danger',
  BROUILLON: 'text-bg-secondary',
  PUBLIEE: 'text-bg-success',
  FERMEE: 'text-bg-dark',
};

@Component({
  selector: 'app-status-badge',
  imports: [LabelPipe],
  template: `<span class="badge rounded-pill {{ cssClass() }}">{{ status() | label: kind() }}</span>`,
})
export class StatusBadge {
  readonly status = input.required<ApplicationStatus | JobOfferStatus>();
  readonly kind = input<'applicationStatus' | 'jobStatus'>('applicationStatus');
  readonly cssClass = computed(() => CLASSES[this.status()] ?? 'text-bg-light');
}
