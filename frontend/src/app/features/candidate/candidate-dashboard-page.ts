import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { errorMessage } from '../../core/http-errors';
import {
  APPLICATION_STATUS_LABELS,
  ApplicationStatus,
  CandidateApplication,
  CandidateDashboard,
  CandidateProfile,
  CvState,
  JobOffer,
} from '../../core/models';
import { ApplicationService } from '../../core/services/application.service';
import { CandidateProfileService } from '../../core/services/candidate-profile.service';
import { DashboardService } from '../../core/services/dashboard.service';
import { JobOfferService } from '../../core/services/job-offer.service';
import { ChartComponent, ChartSeries } from '../../shared/chart';
import { APPLICATION_STATUS_COLORS, PRIMARY_COLOR, shortLabel } from '../../shared/chart-colors';
import { JobCard } from '../../shared/job-card';
import { KpiCard } from '../../shared/kpi-card';
import { LoadingSpinner } from '../../shared/loading-spinner';
import { StatusBadge } from '../../shared/status-badge';

interface ProfileCheck {
  label: string;
  done: boolean;
}

export const CV_STATE_LABELS: Record<CvState, string> = {
  ABSENT: 'Absent',
  IMPORTE: 'Importé, non analysé',
  ANALYSE: 'Analysé',
};

export const QUICK_LINKS = [
  { path: '/candidate/cv', label: 'Mon CV', icon: 'bi-file-earmark-person', testId: 'quick-cv' },
  { path: '/candidate/jobs', label: 'Offres', icon: 'bi-search', testId: 'quick-jobs' },
  { path: '/candidate/applications', label: 'Mes candidatures', icon: 'bi-send', testId: 'quick-applications' },
  { path: '/candidate/assistant', label: 'Assistant RH', icon: 'bi-chat-dots', testId: 'quick-assistant' },
];

const STATUSES = Object.keys(APPLICATION_STATUS_LABELS) as ApplicationStatus[];

/** Tableau de bord candidat : statistiques calculées par le backend (GET /api/candidate/dashboard). */
@Component({
  selector: 'app-candidate-dashboard-page',
  imports: [RouterLink, DatePipe, DecimalPipe, LoadingSpinner, StatusBadge, JobCard, KpiCard, ChartComponent],
  templateUrl: './candidate-dashboard-page.html',
})
export class CandidateDashboardPage implements OnInit {
  private readonly profileService = inject(CandidateProfileService);
  private readonly applicationService = inject(ApplicationService);
  private readonly dashboardService = inject(DashboardService);
  private readonly jobService = inject(JobOfferService);

  readonly quickLinks = QUICK_LINKS;
  readonly cvStateLabels = CV_STATE_LABELS;
  readonly profile = signal<CandidateProfile | null>(null);
  readonly dashboard = signal<CandidateDashboard | null>(null);
  readonly applications = signal<CandidateApplication[]>([]);
  readonly latestJobs = signal<JobOffer[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

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

  readonly matchChart = computed(() => {
    const matches = this.dashboard()?.topMatches ?? [];
    return {
      labels: matches.map((m) => shortLabel(m.title, 24)),
      series: [{ label: 'Score de compatibilité', data: matches.map((m) => m.score), color: PRIMARY_COLOR }] satisfies ChartSeries[],
    };
  });

  readonly cvTone = computed(() => {
    const state = this.dashboard()?.cv.state;
    return state === 'ANALYSE' ? 'success' : state === 'IMPORTE' ? 'warning' : 'secondary';
  });

  readonly cvHint = computed(() => {
    const cv = this.dashboard()?.cv;
    if (!cv || cv.state === 'ABSENT') {
      return 'Importez votre CV pour obtenir des scores';
    }
    if (cv.state === 'IMPORTE') {
      return cv.parsingStatus === 'ECHEC' ? 'Analyse en échec : relancez-la' : 'Analyse en cours';
    }
    return `${cv.extractedSkills} compétence(s) détectée(s)`;
  });

  readonly checks = computed<ProfileCheck[]>(() => {
    const p = this.profile();
    if (!p) {
      return [];
    }
    return [
      { label: 'CV importé', done: p.hasCv },
      { label: 'Titre du profil', done: !!p.headline },
      { label: 'Coordonnées (téléphone, ville)', done: !!p.phone && !!p.location },
      { label: 'Au moins 3 compétences', done: p.skills.length >= 3 },
      { label: 'Une formation', done: p.educations.length > 0 },
      { label: 'Une expérience', done: p.experiences.length > 0 },
    ];
  });

  readonly completion = computed(() => {
    const checks = this.checks();
    return checks.length ? Math.round((checks.filter((c) => c.done).length / checks.length) * 100) : 0;
  });

  ngOnInit(): void {
    forkJoin({
      profile: this.profileService.get(),
      dashboard: this.dashboardService.candidate(),
      applications: this.applicationService.findMine(),
      jobs: this.jobService.search({ page: 0, size: 3 }),
    }).subscribe({
      next: ({ profile, dashboard, applications, jobs }) => {
        this.profile.set(profile);
        this.dashboard.set(dashboard);
        this.applications.set(applications);
        this.latestJobs.set(jobs.content);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(errorMessage(err, 'Impossible de charger le tableau de bord.'));
        this.loading.set(false);
      },
    });
  }
}
