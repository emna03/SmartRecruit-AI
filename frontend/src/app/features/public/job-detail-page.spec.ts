import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { SESSION_STORAGE_KEY } from '../../core/auth/auth.service';
import { CandidateMatching } from '../../core/models';
import { matchingResult } from '../../../testing/matching-fixtures';
import { JobDetailPage } from './job-detail-page';

const JOB = {
  id: 5,
  title: 'Développeur Java',
  description: 'Poste',
  location: 'Tunis',
  contractType: 'CDI',
  minExperienceYears: 3,
  educationLevel: 'BAC_PLUS_5',
  salaryMin: null,
  salaryMax: null,
  status: 'PUBLIEE',
  requiredSkills: ['Java', 'Docker'],
  createdAt: '2026-10-01T09:00:00',
  company: { id: 1, name: 'Acme', sector: 'IT', website: null },
};

function loginAs(role: 'CANDIDAT' | 'RECRUTEUR'): void {
  localStorage.setItem(
    SESSION_STORAGE_KEY,
    JSON.stringify({
      token: 'header.payload.signature',
      user: { id: 1, email: 'alice@test.com', firstName: 'Alice', lastName: 'Test', role },
      expiresAt: Date.now() + 3_600_000,
    }),
  );
}

describe('JobDetailPage — compatibilité', () => {
  let http: HttpTestingController;

  async function open(role: 'CANDIDAT' | 'RECRUTEUR', matching?: CandidateMatching | 'error') {
    loginAs(role);
    TestBed.configureTestingModule({
      imports: [JobDetailPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(JobDetailPage);
    fixture.componentRef.setInput('id', '5');
    fixture.detectChanges();
    await fixture.whenStable();
    http.expectOne('/api/jobs/5').flush(JOB);
    if (role === 'CANDIDAT') {
      http.expectOne('/api/candidate/applications').flush([]);
    }
    await fixture.whenStable();
    if (matching === 'error') {
      http.expectOne('/api/candidate/jobs/5/matching').flush({ message: 'Offre introuvable' }, { status: 404, statusText: 'Not Found' });
    } else if (matching) {
      http.expectOne('/api/candidate/jobs/5/matching').flush(matching);
    }
    await fixture.whenStable();
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    return { el, text: (id: string) => el.querySelector(`[data-testid=${id}]`)?.textContent?.replace(/\s+/g, ' ').trim() ?? '' };
  }

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  it('shows the explained score of a candidate with an analyzed CV', async () => {
    const { text } = await open('CANDIDAT', { status: 'CALCULE', message: 'Score calculé', result: matchingResult() });

    expect(text('matching-score')).toBe('82 %');
    expect(text('missing-skills')).toContain('Docker');
    expect(text('matching-disclaimer')).toContain('pas une décision de recrutement');
  });

  it('invites a candidate without CV to import one', async () => {
    const { el, text } = await open('CANDIDAT', { status: 'CV_ABSENT', message: 'Importez votre CV pour connaître votre compatibilité avec cette offre.', result: null });

    expect(text('matching-status')).toContain('Importez votre CV');
    expect(el.querySelector('[data-testid=matching-status] a')?.getAttribute('href')).toBe('/candidate/cv');
    expect(el.querySelector('[data-testid=matching-result]')).toBeNull();
  });

  it('explains that the CV must be analyzed first', async () => {
    const { text } = await open('CANDIDAT', { status: 'CV_NON_ANALYSE', message: "Votre CV n'a pas encore été analysé.", result: null });

    expect(text('matching-status')).toContain("pas encore été analysé");
  });

  it('keeps the last stored score when the AI service is unavailable', async () => {
    const { text } = await open('CANDIDAT', {
      status: 'IA_INDISPONIBLE',
      message: 'Le service d’analyse est momentanément indisponible. Le dernier score enregistré est affiché.',
      result: matchingResult({ score: 40 }),
    });

    expect(text('matching-status')).toContain('momentanément indisponible');
    expect(text('matching-score')).toBe('40 %');
    expect(text('matching-level')).toBe('Compatibilité faible');
  });

  it('displays an error message when the score cannot be loaded', async () => {
    const { text } = await open('CANDIDAT', 'error');

    expect(text('matching-error')).toContain('Offre introuvable');
  });

  it('does not request a score for a recruiter', async () => {
    const { el } = await open('RECRUTEUR');

    http.expectNone('/api/candidate/jobs/5/matching');
    expect(el.querySelector('[data-testid=matching-card]')).toBeNull();
  });
});
