import { TestBed } from '@angular/core/testing';
import { MatchingResult } from '../core/models';
import { matchingResult } from '../../testing/matching-fixtures';
import { matchLevel } from './match-score-badge';
import { MatchingResultCard } from './matching-result-card';

describe('MatchingResultCard', () => {
  function render(result: MatchingResult, audience: 'candidate' | 'recruiter' = 'candidate') {
    TestBed.configureTestingModule({ imports: [MatchingResultCard] });
    const fixture = TestBed.createComponent(MatchingResultCard);
    fixture.componentRef.setInput('result', result);
    fixture.componentRef.setInput('audience', audience);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    return (id: string) => el.querySelector(`[data-testid=${id}]`)?.textContent?.replace(/\s+/g, ' ').trim() ?? '';
  }

  it('shows the global score, the sub-scores and their weights', () => {
    const text = render(matchingResult());

    expect(text('matching-score')).toBe('82 %');
    expect(text('matching-level')).toBe('Compatibilité élevée');
    expect(text('subscore-skills')).toContain('75 %');
    expect(text('subscore-skills')).toContain('Poids : 60 %');
    expect(text('subscore-experience')).toContain('100 %');
    expect(text('subscore-education')).toContain('80 %');
    expect(text('subscore-education')).toContain('Poids : 15 %');
  });

  it('separates found, related and missing skills', () => {
    const text = render(matchingResult());

    expect(text('matched-skills')).toContain('Java');
    expect(text('matched-skills')).toContain('SQL (via MySQL)');
    expect(text('matched-skills')).not.toContain('Angular');
    expect(text('related-skills')).toContain('Angular (CV : React)');
    expect(text('missing-skills')).toContain('Docker');
    expect(text('missing-skills')).toContain('Kubernetes');
    expect(text('matching-explanation')).toContain('3 sur 4 requises');
    expect(text('matching-meta')).toContain('Algorithme v1.0.0');
  });

  it('marks criteria the offer does not require and redistributes their weight', () => {
    const text = render(matchingResult({ educationScore: null, experienceScore: null, score: 75, skillsScore: 75 }));

    expect(text('subscore-education')).toContain('Non exigé');
    expect(text('subscore-education')).toContain('Non pris en compte');
    expect(text('subscore-experience')).toContain('Non exigé');
    expect(text('subscore-skills')).toContain('Poids : 100 %');
  });

  it('never presents the score as a hiring decision', () => {
    expect(render(matchingResult())('matching-disclaimer')).toContain('ne constitue pas une décision de recrutement');
    TestBed.resetTestingModule();
    const recruiter = render(matchingResult(), 'recruiter')('matching-disclaimer');
    expect(recruiter).toContain('Aide à la décision uniquement');
    expect(recruiter).toContain('ne remplace pas l\'examen de la candidature');
  });

  it('flags a score computed before the last CV update and shows empty lists', () => {
    const text = render(matchingResult({ stale: true, matchedSkills: [], missingSkills: [] }));

    expect(text('matching-stale')).toContain('CV modifié depuis ce calcul');
    expect(text('matched-skills')).toBe('Aucune');
    expect(text('missing-skills')).toBe('Aucune');
  });
});

describe('matchLevel', () => {
  it('uses the same thresholds as the AI explanation (75 / 50)', () => {
    expect(matchLevel(75).label).toBe('Compatibilité élevée');
    expect(matchLevel(74.9).label).toBe('Compatibilité partielle');
    expect(matchLevel(50).css).toBe('warning');
    expect(matchLevel(49.9).css).toBe('danger');
    expect(matchLevel(0).label).toBe('Compatibilité faible');
  });
});
