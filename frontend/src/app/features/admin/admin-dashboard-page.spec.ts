import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ChartConfiguration } from 'chart.js';
import { AdminDashboard } from '../../core/models';
import { fakeChartFactory } from '../../shared/fake-chart.testing';
import { AdminDashboardPage } from './admin-dashboard-page';

const DASHBOARD: AdminDashboard = {
  totalUsers: 7,
  usersByRole: { ADMIN: 1, RECRUTEUR: 3, CANDIDAT: 3 },
  disabledUsers: 1,
  companies: 2,
  recruitersWithoutCompany: 1,
  totalOffers: 4,
  offersByStatus: { BROUILLON: 1, PUBLIEE: 2, FERMEE: 1 },
  publishedOffersByContract: { CDI: 2, CDD: 0, STAGE: 0, ALTERNANCE: 0, FREELANCE: 0 },
  totalApplications: 3,
  applicationsByStatus: { EN_ATTENTE: 2, PRESELECTIONNE: 1, ACCEPTE: 0, REFUSE: 0 },
  candidatesWithCv: 2,
  cvsByParsingStatus: { EN_COURS: 0, TERMINE: 1, ECHEC: 1 },
  storedMatchingScores: 2,
  averageMatchingScore: 65,
  topRequestedSkills: [
    { skill: 'Java', offers: 2 },
    { skill: 'Python', offers: 1 },
  ],
  activityByMonth: ['2026-05', '2026-06', '2026-07', '2026-08', '2026-09', '2026-10'].map((month, i) => ({
    month,
    newUsers: i === 5 ? 7 : 0,
    newOffers: i === 5 ? 4 : 0,
    applications: i === 5 ? 3 : 0,
  })),
};

describe('AdminDashboardPage', () => {
  function setup() {
    const charts = fakeChartFactory();
    TestBed.configureTestingModule({
      imports: [AdminDashboardPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), charts.provider],
    });
    const fixture = TestBed.createComponent(AdminDashboardPage);
    const http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    return { charts, fixture, http, el: fixture.nativeElement as HTMLElement };
  }

  const text = (el: HTMLElement, testId: string) => el.querySelector(`[data-testid=${testId}]`)?.textContent?.replace(/\s+/g, ' ').trim();
  const aiStatus = (ml: boolean) => ({ status: 'UP', service: 'smartrecruit-ai', version: '0.1.0', modules: { ml, matching: true }, responseTimeMs: 4 });

  it('shows the global KPIs of the platform', () => {
    const { fixture, http, el } = setup();
    http.expectOne('/api/admin/dashboard').flush(DASHBOARD);
    http.expectOne('/api/admin/ai/status').flush(aiStatus(true));
    fixture.detectChanges();
    expect(text(el, 'kpi-users')).toContain('7');
    expect(text(el, 'kpi-users')).toContain('1 compte(s) désactivé(s)');
    expect(text(el, 'kpi-candidates')).toContain('3');
    expect(text(el, 'kpi-candidates')).toContain('2 avec un CV');
    expect(text(el, 'kpi-recruiters')).toContain('1 sans entreprise');
    expect(text(el, 'kpi-companies')).toContain('2');
    expect(text(el, 'kpi-offers')).toContain('2 publiée(s)');
    expect(text(el, 'kpi-applications')).toContain('3');
    expect(text(el, 'cv-analyzed')).toBe('1');
    expect(text(el, 'cv-failed')).toBe('1');
    expect(text(el, 'scores-total')).toBe('2');
    expect(text(el, 'scores-average')).toBe('65 / 100');
    http.verify();
  });

  it('charts users by role, offers and applications by status, activity, skills and contracts', () => {
    const { charts, fixture, http } = setup();
    http.expectOne('/api/admin/dashboard').flush(DASHBOARD);
    http.expectOne('/api/admin/ai/status').flush(aiStatus(true));
    fixture.detectChanges();
    const [roles, offers, applications, activity, skills, contracts] = charts.configs as ChartConfiguration[];
    expect(roles.data.labels).toEqual(['Administrateur (1)', 'Recruteur (3)', 'Candidat (3)']);
    expect(offers.data.datasets[0].data).toEqual([1, 2, 1]);
    expect(applications.data.datasets[0].data).toEqual([2, 1, 0, 0]);
    expect(activity.type).toBe('line');
    expect(activity.data.datasets.map((d) => d.data[5])).toEqual([7, 4, 3]);
    expect(skills.data.labels).toEqual(['Java', 'Python']);
    expect(contracts.data.datasets[0].data).toEqual([2, 0, 0, 0, 0]);
  });

  it('only shows the real state of the turnover model, never prediction statistics', () => {
    for (const [response, state] of [
      [aiStatus(true), 'available'],
      [aiStatus(false), 'unavailable'],
      [null, 'unreachable'],
    ] as const) {
      TestBed.resetTestingModule();
      const { fixture, http, el } = setup();
      http.expectOne('/api/admin/dashboard').flush(DASHBOARD);
      const status = http.expectOne('/api/admin/ai/status');
      if (response) {
        status.flush(response);
      } else {
        status.flush({ message: 'Service IA indisponible' }, { status: 503, statusText: 'Unavailable' });
      }
      fixture.detectChanges();
      expect(el.querySelector('[data-testid=ml-state]')?.getAttribute('data-state')).toBe(state);
      expect(text(el, 'turnover-card')).toContain('aucune statistique de prédiction');
    }
  });

  it('keeps the dashboard usable when the AI service is down and reports statistics errors', () => {
    const { fixture, http, el } = setup();
    http.expectOne('/api/admin/dashboard').flush({ message: 'Erreur serveur' }, { status: 500, statusText: 'Error' });
    http.expectOne('/api/admin/ai/status').flush({}, { status: 503, statusText: 'Unavailable' });
    fixture.detectChanges();
    expect(text(el, 'dashboard-error')).toBe('Erreur serveur');
  });
});
