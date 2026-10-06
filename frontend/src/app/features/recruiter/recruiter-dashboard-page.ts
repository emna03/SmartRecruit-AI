import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { errorMessage } from '../../core/http-errors';
import {
  APPLICATION_STATUS_LABELS,
  ApplicationStatus,
  RecruiterDashboard,
  RecruiterProfile,
} from '../../core/models';
import { DashboardService } from '../../core/services/dashboard.service';
import { RecruiterService } from '../../core/services/recruiter.service';
import { ChartComponent, ChartSeries } from '../../shared/chart';
import { APPLICATION_STATUS_COLORS, monthLabel, PRIMARY_COLOR, shortLabel } from '../../shared/chart-colors';
import { KpiCard } from '../../shared/kpi-card';
import { LoadingSpinner } from '../../shared/loading-spinner';
import { StatusBadge } from '../../shared/status-badge';
import { NoCompanyNotice } from './no-company-notice';

const STATUSES = Object.keys(APPLICATION_STATUS_LABELS) as ApplicationStatus[];
/** Nombre maximal d'offres dans le graphique des candidatures (les plus demandées). */
export const MAX_OFFERS_IN_CHART = 10;

/** Tableau de bord recruteur : statistiques des offres de son entreprise (GET /api/recruiter/dashboard). */
@Component({
  selector: 'app-recruiter-dashboard-page',
  imports: [RouterLink, DatePipe, DecimalPipe, LoadingSpinner, StatusBadge, NoCompanyNotice, KpiCard, ChartComponent],
  templateUrl: './recruiter-dashboard-page.html',
})
export class RecruiterDashboardPage implements OnInit {
  private readonly recruiterService = inject(RecruiterService);
  private readonly dashboardService = inject(DashboardService);

  readonly profile = signal<RecruiterProfile | null>(null);
  readonly dashboard = signal<RecruiterDashboard | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  /** Offres ayant reçu le plus de candidatures (à égalité, la plus récente d'abord, ordre du backend). */
  readonly chartOffers = computed(() =>
    [...(this.dashboard()?.offers ?? [])]
      .filter((o) => o.applications > 0)
      .sort((a, b) => b.applications - a.applications)
      .slice(0, MAX_OFFERS_IN_CHART),
  );

  readonly applicationsPerOffer = computed(() => {
    const offers = this.chartOffers();
    return {
      labels: offers.map((o) => shortLabel(o.title)),
      series: STATUSES.map((s) => ({
        label: APPLICATION_STATUS_LABELS[s],
        data: offers.map((o) => o.applicationsByStatus[s]),
        color: APPLICATION_STATUS_COLORS[s],
      })) satisfies ChartSeries[],
      height: Math.max(180, offers.length * 38 + 70),
    };
  });

  readonly statusChart = computed(() => {
    const byStatus = this.dashboard()?.applicationsByStatus;
    return {
      labels: STATUSES.map((s) => `${APPLICATION_STATUS_LABELS[s]} (${byStatus?.[s] ?? 0})`),
      series: [
        {
          label: 'Candidatures',
          data: STATUSES.map((s) => byStatus?.[s] ?? 0),
          color: STATUSES.map((s) => APPLICATION_STATUS_COLORS[s]),
        },
      ] satisfies ChartSeries[],
    };
  });

  readonly scoreChart = computed(() => {
    const offers = (this.dashboard()?.offers ?? []).filter((o) => o.averageScore !== null).slice(0, MAX_OFFERS_IN_CHART);
    return {
      labels: offers.map((o) => shortLabel(o.title)),
      series: [
        { label: 'Score moyen', data: offers.map((o) => o.averageScore ?? 0), color: PRIMARY_COLOR },
        { label: 'Meilleur score', data: offers.map((o) => o.bestScore ?? 0), color: '#16a34a' },
      ] satisfies ChartSeries[],
    };
  });

  readonly monthChart = computed(() => {
    const months = this.dashboard()?.applicationsByMonth ?? [];
    return {
      labels: months.map((m) => monthLabel(m.month)),
      series: [{ label: 'Candidatures reçues', data: months.map((m) => m.count), color: PRIMARY_COLOR }] satisfies ChartSeries[],
    };
  });

  ngOnInit(): void {
    forkJoin({ profile: this.recruiterService.getProfile(), dashboard: this.dashboardService.recruiter() }).subscribe({
      next: ({ profile, dashboard }) => {
        this.profile.set(profile);
        this.dashboard.set(dashboard);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(errorMessage(err, 'Impossible de charger le tableau de bord.'));
        this.loading.set(false);
      },
    });
  }
}
