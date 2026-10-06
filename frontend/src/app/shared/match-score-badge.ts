import { DecimalPipe } from '@angular/common';
import { Component, computed, input } from '@angular/core';

/** Seuils identiques à ceux de l'explication produite par le service IA (version 1.0.0). */
export const HIGH_MATCH = 75;
export const MEDIUM_MATCH = 50;

export interface MatchLevel {
  label: string;
  css: string;
}

export function matchLevel(score: number): MatchLevel {
  if (score >= HIGH_MATCH) {
    return { label: 'Compatibilité élevée', css: 'success' };
  }
  if (score >= MEDIUM_MATCH) {
    return { label: 'Compatibilité partielle', css: 'warning' };
  }
  return { label: 'Compatibilité faible', css: 'danger' };
}

/** Pastille « 82 % » colorée selon le niveau de compatibilité. */
@Component({
  selector: 'app-match-score-badge',
  imports: [DecimalPipe],
  template: `<span class="badge rounded-pill text-bg-{{ level().css }}" [title]="level().label" data-testid="match-score">
    {{ score() | number: '1.0-1' }} %</span>`,
})
export class MatchScoreBadge {
  readonly score = input.required<number>();
  readonly level = computed(() => matchLevel(this.score()));
}
