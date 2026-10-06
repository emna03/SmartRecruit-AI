import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MatchingResult, RecruiterApplication } from '../../core/models';
import { matchingResult } from '../../../testing/matching-fixtures';
import { JobCandidatesPage } from './job-candidates-page';

const JOB = {
  id: 5,
  title: 'Développeur Java',
  description: 'Poste',
  location: 'Tunis',
  contractType: 'CDI',
  minExperienceYears: 3,
  educationLevel: 'BAC_PLUS_5',
  status: 'PUBLIEE',
  requiredSkills: ['Java', 'Docker'],
  createdAt: '2026-10-01T09:00:00',
  company: { id: 1, name: 'Acme' },
};

function application(id: number, name: string, matching: MatchingResult | null, cv = true): RecruiterApplication {
  return {
    id,
    jobOfferId: 5,
    jobTitle: 'Développeur Java',
    status: 'EN_ATTENTE',
    appliedAt: '2026-10-05T09:00:00',
    candidate: {
      candidateId: id,
      firstName: name,
      lastName: 'Test',
      email: `${name.toLowerCase()}@test.com`,
      headline: null,
      location: null,
      yearsOfExperience: null,
      educationLevel: null,
      skills: [],
      hasCv: cv,
      cvAnalyzed: cv,
    },
    matching,
  };
}

describe('JobCandidatesPage — classement par score', () => {
  let http: HttpTestingController;

  async function open(applications: RecruiterApplication[]) {
    TestBed.configureTestingModule({
      imports: [JobCandidatesPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(JobCandidatesPage);
    fixture.componentRef.setInput('id', '5');
    fixture.detectChanges();
    http.expectOne('/api/recruiter/jobs/5').flush(JOB);
    http.expectOne('/api/recruiter/jobs/5/applications').flush(applications);
    await fixture.whenStable();
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const cards = () => Array.from(el.querySelectorAll('[data-testid=candidate-card]')).map((c) => c.textContent?.replace(/\s+/g, ' ') ?? '');
    return { fixture, el, cards };
  }

  afterEach(() => http.verify());

  it('shows the ranking returned by the server with rank, score and missing skills', async () => {
    const { el, cards } = await open([
      application(1, 'Haute', matchingResult({ score: 90, missingSkills: ['Kubernetes'] })),
      application(2, 'Moyenne', matchingResult({ score: 55 })),
      application(3, 'Basse', matchingResult({ score: 19, stale: true })),
      application(4, 'Sanscv', null, false),
    ]);

    const list = cards();
    expect(list).toHaveLength(4);
    expect(list[0]).toContain('#1');
    expect(list[0]).toContain('90 %');
    expect(list[0]).toContain('Kubernetes');
    expect(list[1]).toContain('#2');
    expect(list[2]).toContain('#3');
    expect(list[2]).toContain('CV modifié');
    expect(list[3]).toContain('Sans score');
    expect(list[3]).toContain('Aucun CV importé');
    expect(el.querySelector('[data-testid=ranking-disclaimer]')?.textContent).toContain('aide à la décision');
    expect(el.querySelector('[data-testid=stale-warning]')?.textContent).toContain('1 score(s)');
  });

  it('recomputes the scores and displays the new ranking', async () => {
    const { fixture, el, cards } = await open([application(1, 'Alice', null), application(2, 'Bob', null)]);
    const button = el.querySelector<HTMLButtonElement>('[data-testid=refresh-scores]')!;
    expect(button.textContent).toContain('Calculer les scores');

    button.click();
    http.expectOne({ method: 'POST', url: '/api/recruiter/jobs/5/matching' }).flush({
      computed: 2,
      withoutAnalyzedCv: 0,
      applications: [application(2, 'Bob', matchingResult({ score: 70 })), application(1, 'Alice', matchingResult({ score: 30 }))],
    });
    await fixture.whenStable();
    fixture.detectChanges();

    const list = cards();
    expect(list[0]).toContain('Bob');
    expect(list[0]).toContain('70 %');
    expect(list[1]).toContain('Alice');
    expect(el.querySelector('[data-testid=refresh-scores]')?.textContent).toContain('Recalculer les scores');
  });

  it('keeps the list when the AI service is unavailable', async () => {
    const { fixture, cards } = await open([application(1, 'Alice', matchingResult({ score: 60 }))]);

    fixture.componentInstance.refreshScores();
    http.expectOne('/api/recruiter/jobs/5/matching').flush(
      { status: 503, message: 'Le service IA est momentanément indisponible. Réessayez plus tard.' },
      { status: 503, statusText: 'Service Unavailable' },
    );
    await fixture.whenStable();
    fixture.detectChanges();

    expect(cards()[0]).toContain('60 %');
    expect(fixture.componentInstance.refreshing()).toBe(false);
  });
});
