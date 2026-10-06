import { registerLocaleData } from '@angular/common';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import localeFr from '@angular/common/locales/fr';
import { LOCALE_ID } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ChartConfiguration } from 'chart.js';
import { RecruiterDashboard, RecruiterOfferStats, RecruiterProfile } from '../../core/models';
import { fakeChartFactory } from '../../shared/fake-chart.testing';
import { RecruiterDashboardPage } from './recruiter-dashboard-page';

registerLocaleData(localeFr);

const PROFILE = { firstName: 'Bob', company: { id: 1, name: 'Acme' } } as unknown as RecruiterProfile;

function offer(id: number, title: string, statusCounts: [number, number, number, number], average: number | null, best: number | null): RecruiterOfferStats {
  const [EN_ATTENTE, PRESELECTIONNE, ACCEPTE, REFUSE] = statusCounts;
  return {
    id,
    title,
    status: 'PUBLIEE',
    createdAt: '2026-10-01T10:00:00',
    applications: EN_ATTENTE + PRESELECTIONNE + ACCEPTE + REFUSE,
    applicationsByStatus: { EN_ATTENTE, PRESELECTIONNE, ACCEPTE, REFUSE },
    scoredApplications: average === null ? 0 : 1,
    averageScore: average,
    bestScore: best,
  };
}

const MONTHS = ['2026-05', '2026-06', '2026-07', '2026-08', '2026-09', '2026-10'];

const DASHBOARD: RecruiterDashboard = {
  hasCompany: true,
  companyName: 'Acme',
  totalOffers: 3,
  offersByStatus: { BROUILLON: 1, PUBLIEE: 2, FERMEE: 0 },
  totalApplications: 4,
  applicationsByStatus: { EN_ATTENTE: 2, PRESELECTIONNE: 0, ACCEPTE: 1, REFUSE: 1 },
  applicationsByMonth: MONTHS.map((month, i) => ({ month, count: i === 5 ? 4 : 0 })),
  scoredApplications: 3,
  averageMatchingScore: 61.8,
  offers: [offer(3, 'Data', [0, 0, 0, 0], null, null), offer(2, 'Dev Python', [1, 0, 0, 0], 45.5, 45.5), offer(1, 'Dev Java', [1, 0, 1, 1], 70, 80)],
  topCandidates: [
    { applicationId: 11, candidateName: 'Alice Martin', jobOfferId: 1, jobTitle: 'Dev Java', score: 80, status: 'ACCEPTE', stale: false },
    { applicationId: 12, candidateName: 'Carl Ben Ali', jobOfferId: 1, jobTitle: 'Dev Java', score: 60, status: 'REFUSE', stale: true },
  ],
};

describe('RecruiterDashboardPage', () => {
  function setup() {
    const charts = fakeChartFactory();
    TestBed.configureTestingModule({
      imports: [RecruiterDashboardPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        charts.provider,
        { provide: LOCALE_ID, useValue: 'fr-FR' },
      ],
    });
    const fixture = TestBed.createComponent(RecruiterDashboardPage);
    const http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    return { charts, fixture, http, el: fixture.nativeElement as HTMLElement };
  }

  function respond(http: HttpTestingController, dashboard: RecruiterDashboard, profile = PROFILE) {
    http.expectOne('/api/recruiter/profile').flush(profile);
    http.expectOne('/api/recruiter/dashboard').flush(dashboard);
  }

  const text = (el: HTMLElement, testId: string) => el.querySelector(`[data-testid=${testId}]`)?.textContent?.replace(/\s+/g, ' ').trim();

  it('shows offer, application and matching KPIs of the company', () => {
    const { fixture, http, el } = setup();
    respond(http, DASHBOARD);
    fixture.detectChanges();
    expect(el.querySelector('.page-subtitle')?.textContent).toContain('Acme');
    expect(text(el, 'kpi-offers')).toContain('3');
    expect(text(el, 'kpi-published')).toContain('2');
    expect(text(el, 'kpi-drafts')).toContain('1');
    expect(text(el, 'kpi-closed')).toContain('0');
    expect(text(el, 'kpi-applications')).toContain('4');
    expect(text(el, 'kpi-average-score')).toContain('61,8 / 100');
    expect(text(el, 'kpi-average-score')).toContain('3 candidature(s) avec score');
    http.verify();
  });

  it('charts candidates per offer and status, statuses, scores per offer and months', () => {
    const { charts, fixture, http } = setup();
    respond(http, DASHBOARD);
    fixture.detectChanges();
    const [perOffer, status, scores, months] = charts.configs as ChartConfiguration[];
    // Offres triées par nombre de candidatures ; une offre sans candidature n'apparaît pas dans ce graphique.
    expect(perOffer.data.labels).toEqual(['Dev Java', 'Dev Python']);
    expect(perOffer.data.datasets.map((d) => [d.label, d.data])).toEqual([
      ['En attente', [1, 1]],
      ['Présélectionné', [0, 0]],
      ['Accepté', [1, 0]],
      ['Refusé', [1, 0]],
    ]);
    expect(perOffer.options?.indexAxis).toBe('y');
    expect(status.type).toBe('doughnut');
    expect(status.data.datasets[0].data).toEqual([2, 0, 1, 1]);
    expect(scores.data.labels).toEqual(['Dev Python', 'Dev Java']);
    expect(scores.data.datasets.map((d) => d.data)).toEqual([[45.5, 70], [45.5, 80]]);
    expect(months.type).toBe('line');
    expect(months.data.datasets[0].data).toEqual([0, 0, 0, 0, 0, 4]);
    expect(months.data.labels).toHaveLength(6);
  });

  it('lists the best candidates as an indication, never as a decision', () => {
    const { fixture, http, el } = setup();
    respond(http, DASHBOARD);
    fixture.detectChanges();
    const rows = el.querySelectorAll('[data-testid=top-candidate]');
    expect(rows).toHaveLength(2);
    expect(rows[0].textContent).toContain('Alice Martin');
    expect(rows[0].textContent).toContain('80 / 100');
    expect(rows[1].textContent).toContain('À recalculer');
    expect(rows[0].querySelector('a')?.getAttribute('href')).toBe('/recruiter/applications/11');
    expect(text(el, 'top-candidates')?.length).toBeGreaterThan(0);
    expect(el.textContent).toContain('jamais une décision automatique');
    expect([...el.querySelectorAll('[data-testid=offer-applications]')].map((c) => c.textContent?.trim())).toEqual(['0', '1', '3']);
  });

  it('shows empty states for a company without applications', () => {
    const { charts, fixture, http, el } = setup();
    respond(http, {
      ...DASHBOARD,
      totalApplications: 0,
      applicationsByStatus: { EN_ATTENTE: 0, PRESELECTIONNE: 0, ACCEPTE: 0, REFUSE: 0 },
      applicationsByMonth: MONTHS.map((month) => ({ month, count: 0 })),
      scoredApplications: 0,
      averageMatchingScore: null,
      offers: [offer(3, 'Data', [0, 0, 0, 0], null, null)],
      topCandidates: [],
    });
    fixture.detectChanges();
    expect(charts.configs).toHaveLength(0);
    expect(text(el, 'chart-applications-per-offer')).toContain('Aucune candidature reçue');
    expect(text(el, 'chart-scores-per-offer')).toContain('Aucun score');
    expect(text(el, 'top-candidates-empty')).toContain('Aucun candidat');
    expect(text(el, 'kpi-average-score')).toContain('—');
  });

  it('asks a recruiter without company to create it first', () => {
    const { fixture, http, el } = setup();
    respond(
      http,
      { ...DASHBOARD, hasCompany: false, companyName: null, totalOffers: 0, offers: [], topCandidates: [] },
      { ...PROFILE, company: null } as unknown as RecruiterProfile,
    );
    fixture.detectChanges();
    expect(el.querySelector('app-no-company-notice')).not.toBeNull();
    expect(el.querySelector('[data-testid=kpi-offers]')).toBeNull();
  });

  it('displays a clear error when the statistics cannot be loaded', () => {
    const { fixture, http, el } = setup();
    http.expectOne('/api/recruiter/profile').flush(PROFILE);
    http.expectOne('/api/recruiter/dashboard').flush({ message: 'Erreur serveur' }, { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    expect(text(el, 'dashboard-error')).toBe('Erreur serveur');
  });
});
