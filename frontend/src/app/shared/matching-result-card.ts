import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, input } from '@angular/core';
import { MatchingResult } from '../core/models';
import { matchLevel } from './match-score-badge';

interface SubScore {
  label: string;
  icon: string;
  value: number | null;
  weight: number;
  testId: string;
}

/** Pondérations de l'algorithme 1.0.0 (redistribuées si l'offre n'exige pas un critère). */
const WEIGHTS = { skills: 0.6, experience: 0.25, education: 0.15 };

/**
 * Résultat de matching expliqué : score global, sous-scores, compétences trouvées / proches / manquantes,
 * explication et rappel que le score est une aide, pas une décision.
 */
@Component({
  selector: 'app-matching-result-card',
  imports: [DatePipe, DecimalPipe],
  templateUrl: './matching-result-card.html',
})
export class MatchingResultCard {
  readonly result = input.required<MatchingResult>();
  /** Vue recruteur : le rappel « aide à la décision » s'adresse au recruteur. */
  readonly audience = input<'candidate' | 'recruiter'>('candidate');

  readonly level = computed(() => matchLevel(this.result().score));
  readonly fullMatches = computed(() => this.result().matchedSkills.filter((s) => s.matchType !== 'RELATED'));
  readonly relatedMatches = computed(() => this.result().matchedSkills.filter((s) => s.matchType === 'RELATED'));
  /** Date UTC renvoyée par le serveur, affichée dans le fuseau du navigateur. */
  readonly computedAt = computed(() => {
    const value = this.result().computedAt;
    return /[zZ]|[+-]\d{2}:\d{2}$/.test(value) ? value : `${value}Z`;
  });

  readonly subScores = computed<SubScore[]>(() => {
    const r = this.result();
    const applicable = WEIGHTS.skills
      + (r.experienceScore === null ? 0 : WEIGHTS.experience)
      + (r.educationScore === null ? 0 : WEIGHTS.education);
    const weight = (base: number, value: number | null) => (value === null ? 0 : base / applicable);
    return [
      { label: 'Compétences', icon: 'bi-tools', value: r.skillsScore, weight: weight(WEIGHTS.skills, r.skillsScore), testId: 'subscore-skills' },
      { label: 'Expérience', icon: 'bi-briefcase', value: r.experienceScore, weight: weight(WEIGHTS.experience, r.experienceScore), testId: 'subscore-experience' },
      { label: 'Formation', icon: 'bi-mortarboard', value: r.educationScore, weight: weight(WEIGHTS.education, r.educationScore), testId: 'subscore-education' },
    ];
  });

  barClass(value: number): string {
    return `bg-${matchLevel(value).css}`;
  }
}
