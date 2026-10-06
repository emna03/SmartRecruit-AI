import { NgTemplateOutlet } from '@angular/common';
import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';

/** Carte d'indicateur : valeur principale, libellé, précision facultative, lien facultatif. */
@Component({
  selector: 'app-kpi-card',
  imports: [RouterLink, NgTemplateOutlet],
  template: `
    @if (link(); as target) {
      <a class="card stat-card card-hover h-100 text-decoration-none text-reset" [routerLink]="target">
        <ng-container *ngTemplateOutlet="body" />
      </a>
    } @else {
      <div class="card stat-card h-100"><ng-container *ngTemplateOutlet="body" /></div>
    }
    <ng-template #body>
      <div class="card-body">
        <span class="stat-icon bg-{{ tone() }}-subtle text-{{ tone() }}-emphasis mb-2"><i aria-hidden="true" class="bi {{ icon() }}"></i></span>
        <div class="stat-value" data-testid="kpi-value">{{ value() }}</div>
        <div class="stat-label">{{ label() }}</div>
        @if (hint()) {
          <div class="small text-secondary mt-1" data-testid="kpi-hint">{{ hint() }}</div>
        }
      </div>
    </ng-template>
  `,
  host: { class: 'd-block h-100' },
})
export class KpiCard {
  readonly label = input.required<string>();
  readonly value = input.required<string | number>();
  readonly icon = input.required<string>();
  readonly tone = input('primary');
  readonly hint = input<string | null>(null);
  readonly link = input<string | null>(null);
}
