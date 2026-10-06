import { DatePipe } from '@angular/common';
import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { JobOffer } from '../core/models';
import { LabelPipe } from './label.pipe';

@Component({
  selector: 'app-job-card',
  imports: [RouterLink, LabelPipe, DatePipe],
  template: `
    <a class="card card-hover h-100 text-decoration-none text-reset" [routerLink]="[detailBase(), job().id]">
      <div class="card-body d-flex flex-column">
        <div class="d-flex justify-content-between align-items-start gap-2 mb-2">
          <h3 class="h6 fw-semibold mb-0 job-title">{{ job().title }}</h3>
          <span class="badge bg-primary-soft text-primary flex-shrink-0">{{ job().contractType | label: 'contract' }}</span>
        </div>
        <div class="text-secondary small mb-3">
          <i aria-hidden="true" class="bi bi-building me-1"></i>{{ job().company.name }}
          @if (job().location) {
            <span class="ms-2"><i aria-hidden="true" class="bi bi-geo-alt me-1"></i>{{ job().location }}</span>
          }
        </div>
        <div class="mb-3">
          @for (skill of job().requiredSkills.slice(0, 5); track skill) {
            <span class="skill-badge">{{ skill }}</span>
          }
          @if (job().requiredSkills.length > 5) {
            <span class="text-secondary small">+{{ job().requiredSkills.length - 5 }}</span>
          }
        </div>
        <div class="mt-auto d-flex justify-content-between text-secondary small">
          <span><i aria-hidden="true" class="bi bi-briefcase me-1"></i>{{ job().minExperienceYears }} an(s) d'exp. min.</span>
          <span>{{ job().createdAt | date: 'dd/MM/yyyy' }}</span>
        </div>
      </div>
    </a>
  `,
})
export class JobCard {
  readonly job = input.required<JobOffer>();
  readonly detailBase = input('/jobs', { transform: (v: string | undefined) => v ?? '/jobs' });
}
