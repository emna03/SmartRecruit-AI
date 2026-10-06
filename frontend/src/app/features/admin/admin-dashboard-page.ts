import { DecimalPipe } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { errorMessage } from '../../core/http-errors';
import {
  AdminDashboard,
  APPLICATION_STATUS_LABELS,
  ApplicationStatus,
  CONTRACT_TYPE_LABELS,
  ContractType,
  JOB_STATUS_LABELS,
  JobOfferStatus,
  Role,
  ROLE_LABELS,
} from '../../core/models';
import { AdminService } from '../../core/services/admin.service';
import { DashboardService } from '../../core/services/dashboard.service';
import { ChartComponent, ChartSeries } from '../../shared/chart';
import {
  ACTIVITY_COLORS,
  APPLICATION_STATUS_COLORS,
  JOB_STATUS_COLORS,
  monthLabel,
  PRIMARY_COLOR,
  ROLE_COLORS,
} from '../../shared/chart-colors';
import { KpiCard } from '../../shared/kpi-card';
import { LoadingSpinner } from '../../shared/loading-spinner';

/** État du modèle de turnover vu par Spring Boot : seule information réellement disponible sur ce module. */
export type MlState = 'loading' | 'available' | 'unavailable' | 'unreachable';

const ROLES = Object.keys(ROLE_LABELS) as Role[];
const JOB_STATUSES = Object.keys(JOB_STATUS_LABELS) as JobOfferStatus[];
const APPLICATION_STATUSES = Object.keys(APPLICATION_STATUS_LABELS) as ApplicationStatus[];
const CONTRACTS = Object.keys(CONTRACT_TYPE_LABELS) as ContractType[];

function distribution<K extends string>(
  keys: K[],
  labels: Record<K, string>,
  counts: Record<K, number> | undefined,
  colors: Record<K, string>,
  seriesLabel: string,
) {
  return {
    labels: keys.map((k) => `${labels[k]} (${counts?.[k] ?? 0})`),
    series: [{ label: seriesLabel, data: keys.map((k) => counts?.[k] ?? 0), color: keys.map((k) => colors[k]) }] satisfies ChartSeries[],
  };
}

/** Tableau de bord administrateur : statistiques globales calculées par le backend (GET /api/admin/dashboard). */
@Component({
  selector: 'app-admin-dashboard-page',
  imports: [RouterLink, DecimalPipe, LoadingSpinner, KpiCard, ChartComponent],
  templateUrl: './admin-dashboard-page.html',
})
export class AdminDashboardPage implements OnInit {
  private readonly dashboardService = inject(DashboardService);
  private readonly adminService = inject(AdminService);

  readonly dashboard = signal<AdminDashboard | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly mlState = signal<MlState>('loading');

  readonly rolesChart = computed(() =>
    distribution(ROLES, ROLE_LABELS, this.dashboard()?.usersByRole, ROLE_COLORS, 'Utilisateurs'),
  );

  readonly offersChart = computed(() =>
    distribution(JOB_STATUSES, JOB_STATUS_LABELS, this.dashboard()?.offersByStatus, JOB_STATUS_COLORS, 'Offres'),
  );

  readonly applicationsChart = computed(() =>
    distribution(
      APPLICATION_STATUSES,
      APPLICATION_STATUS_LABELS,
      this.dashboard()?.applicationsByStatus,
      APPLICATION_STATUS_COLORS,
      'Candidatures',
    ),
  );

  readonly activityChart = computed(() => {
    const months = this.dashboard()?.activityByMonth ?? [];
    return {
      labels: months.map((m) => monthLabel(m.month)),
      series: [
        { label: 'Nouveaux utilisateurs', data: months.map((m) => m.newUsers), color: ACTIVITY_COLORS.users },
        { label: 'Offres créées', data: months.map((m) => m.newOffers), color: ACTIVITY_COLORS.offers },
        { label: 'Candidatures', data: months.map((m) => m.applications), color: ACTIVITY_COLORS.applications },
      ] satisfies ChartSeries[],
    };
  });

  readonly skillsChart = computed(() => {
    const skills = this.dashboard()?.topRequestedSkills ?? [];
    return {
      labels: skills.map((s) => s.skill),
      series: [{ label: 'Offres publiées', data: skills.map((s) => s.offers), color: PRIMARY_COLOR }] satisfies ChartSeries[],
    };
  });

  readonly contractChart = computed(() => {
    const counts = this.dashboard()?.publishedOffersByContract;
    return {
      labels: CONTRACTS.map((c) => CONTRACT_TYPE_LABELS[c]),
      series: [{ label: 'Offres publiées', data: CONTRACTS.map((c) => counts?.[c] ?? 0), color: '#16a34a' }] satisfies ChartSeries[],
    };
  });

  ngOnInit(): void {
    this.dashboardService.admin().subscribe({
      next: (dashboard) => {
        this.dashboard.set(dashboard);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(errorMessage(err, 'Impossible de charger les statistiques.'));
        this.loading.set(false);
      },
    });
    this.adminService.aiStatus().subscribe({
      next: (status) => this.mlState.set(status.modules['ml'] ? 'available' : 'unavailable'),
      error: () => this.mlState.set('unreachable'),
    });
  }
}
