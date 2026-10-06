import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ChartConfiguration } from 'chart.js';
import { CandidateDashboard, CandidateProfile } from '../../core/models';
import { fakeChartFactory } from '../../shared/fake-chart.testing';
import { CandidateDashboardPage } from './candidate-dashboard-page';

const PROFILE = {
  firstName: 'Alice',
  hasCv: true,
  headline: null,
  phone: null,
  location: null,
  skills: [],
  educations: [],
  experiences: [],
} as unknown as CandidateProfile;

const DASHBOARD: CandidateDashboard = {
  totalApplications: 3,
  applicationsByStatus: { EN_ATTENTE: 1, PRESELECTIONNE: 1, ACCEPTE: 0, REFUSE: 1 },
  publishedOffers: 7,
  publishedOffersNotApplied: 5,
  cv: { state: 'ANALYSE', parsingStatus: 'TERMINE', updatedAt: '2026-10-01T10:00:00', parsedAt: '2026-10-01T10:00:05', extractedSkills: 6 },
  scoredOffers: 2,
  topMatches: [
    { jobOfferId: 4, title: 'Dev Java', companyName: 'Acme', offerStatus: 'PUBLIEE', score: 80, computedAt: '2026-10-02T09:00:00', stale: false, applied: true },
    { jobOfferId: 9, title: 'Dev Python', companyName: 'Globex', offerStatus: 'FERMEE', score: 45.5, computedAt: '2026-10-02T09:00:00', stale: true, applied: false },
  ],
};

const EMPTY: CandidateDashboard = {
  totalApplications: 0,
  applicationsByStatus: { EN_ATTENTE: 0, PRESELECTIONNE: 0, ACCEPTE: 0, REFUSE: 0 },
  publishedOffers: 0,
  publishedOffersNotApplied: 0,
  cv: { state: 'ABSENT', parsingStatus: null, updatedAt: null, parsedAt: null, extractedSkills: 0 },
  scoredOffers: 0,
  topMatches: [],
};

describe('CandidateDashboardPage', () => {
  function setup() {
    const charts = fakeChartFactory();
    TestBed.configureTestingModule({
      imports: [CandidateDashboardPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), charts.provider],
    });
    const fixture = TestBed.createComponent(CandidateDashboardPage);
    const http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    return { charts, fixture, http, el: fixture.nativeElement as HTMLElement };
  }

  function respond(http: HttpTestingController, dashboard: CandidateDashboard | null) {
    http.expectOne('/api/candidate/profile').flush(PROFILE);
    const stats = http.expectOne('/api/candidate/dashboard');
    if (dashboard) {
      stats.flush(dashboard);
    } else {
      stats.flush({ status: 503, message: 'Service indisponible' }, { status: 503, statusText: 'Unavailable' });
      return;
    }
    http.expectOne('/api/candidate/applications').flush([]);
    http.expectOne((r) => r.url === '/api/jobs').flush({ content: [], page: 0, size: 3, totalElements: 0, totalPages: 0 });
  }

  const text = (el: HTMLElement, testId: string) => el.querySelector(`[data-testid=${testId}]`)?.textContent?.replace(/\s+/g, ' ').trim();

  it('shows the KPIs computed by the backend', () => {
    const { fixture, http, el } = setup();
    respond(http, DASHBOARD);
    fixture.detectChanges();
    expect(text(el, 'kpi-applications')).toContain('3');
    expect(text(el, 'kpi-offers')).toContain('7');
    expect(text(el, 'kpi-offers')).toContain('5 sans candidature de votre part');
    expect(text(el, 'kpi-cv')).toContain('Analysé');
    expect(text(el, 'kpi-cv')).toContain('6 compétence(s) détectée(s)');
    expect(text(el, 'kpi-best-score')).toContain('80 / 100');
    expect(text(el, 'kpi-best-score')).toContain('2 offre(s) avec un score');
    http.verify();
  });

  it('offers quick links to the CV, the offers, the applications and the HR assistant', () => {
    const { fixture, http, el } = setup();
    respond(http, DASHBOARD);
    fixture.detectChanges();
    const links = ['quick-cv', 'quick-jobs', 'quick-applications', 'quick-assistant'].map((id) =>
      el.querySelector(`[data-testid=${id}]`)?.getAttribute('href'),
    );
    expect(links).toEqual(['/candidate/cv', '/candidate/jobs', '/candidate/applications', '/candidate/assistant']);
  });

  it('charts applications by status and the best matching scores, with the disclaimer', () => {
    const { charts, fixture, http, el } = setup();
    respond(http, DASHBOARD);
    fixture.detectChanges();
    const [status, matches] = charts.configs as ChartConfiguration[];
    expect(status.type).toBe('doughnut');
    expect(status.data.labels).toEqual(['En attente (1)', 'Présélectionné (1)', 'Accepté (0)', 'Refusé (1)']);
    expect(status.data.datasets[0].data).toEqual([1, 1, 0, 1]);
    expect(matches.type).toBe('bar');
    expect(matches.data.datasets[0].data).toEqual([80, 45.5]);
    expect(matches.data.labels).toEqual(['Dev Java', 'Dev Python']);
    const items = el.querySelectorAll('[data-testid=top-match]');
    expect(items).toHaveLength(2);
    expect(items[0].textContent).toContain('Candidature envoyée');
    expect(items[1].textContent).toContain('Offre plus publiée');
    expect(items[1].textContent).toContain('À recalculer');
    expect(items[0].querySelector('a')?.getAttribute('href')).toBe('/candidate/jobs/4');
    expect(text(el, 'chart-top-matches')).toContain('ne constituent pas une décision de recrutement');
  });

  it('shows empty states when the candidate has no data yet', () => {
    const { charts, fixture, http, el } = setup();
    respond(http, EMPTY);
    fixture.detectChanges();
    expect(charts.configs).toHaveLength(0);
    expect(text(el, 'chart-applications-status')).toContain("Vous n'avez pas encore postulé.");
    expect(text(el, 'chart-top-matches')).toContain('Aucun score');
    expect(text(el, 'kpi-cv')).toContain('Absent');
    expect(text(el, 'kpi-cv')).toContain('Importez votre CV');
    expect(text(el, 'kpi-best-score')).toContain('—');
  });

  it('displays a clear error when the statistics cannot be loaded', () => {
    const { fixture, http, el } = setup();
    respond(http, null);
    fixture.detectChanges();
    expect(text(el, 'dashboard-error')).toBe('Service indisponible');
    expect(el.querySelector('[data-testid=kpi-applications]')).toBeNull();
  });
});
