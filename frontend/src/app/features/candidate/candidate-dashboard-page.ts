import { DatePipe } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { errorMessage } from '../../core/http-errors';
import { CandidateApplication, CandidateProfile, JobOffer } from '../../core/models';
import { ApplicationService } from '../../core/services/application.service';
import { CandidateProfileService } from '../../core/services/candidate-profile.service';
import { JobOfferService } from '../../core/services/job-offer.service';
import { JobCard } from '../../shared/job-card';
import { LoadingSpinner } from '../../shared/loading-spinner';
import { StatusBadge } from '../../shared/status-badge';

interface ProfileCheck {
  label: string;
  done: boolean;
}

/** Tableau de bord candidat (version simple ; graphiques et scores de matching aux étapes suivantes). */
@Component({
  selector: 'app-candidate-dashboard-page',
  imports: [RouterLink, DatePipe, LoadingSpinner, StatusBadge, JobCard],
  templateUrl: './candidate-dashboard-page.html',
})
export class CandidateDashboardPage implements OnInit {
  private readonly profileService = inject(CandidateProfileService);
  private readonly applicationService = inject(ApplicationService);
  private readonly jobService = inject(JobOfferService);

  readonly profile = signal<CandidateProfile | null>(null);
  readonly applications = signal<CandidateApplication[]>([]);
  readonly latestJobs = signal<JobOffer[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  readonly stats = computed(() => {
    const list = this.applications();
    const count = (status: string) => list.filter((a) => a.status === status).length;
    return [
      { label: 'Candidatures envoyées', value: list.length, icon: 'bi-send', tone: 'primary' },
      { label: 'En attente', value: count('EN_ATTENTE'), icon: 'bi-hourglass-split', tone: 'warning' },
      { label: 'Présélectionné', value: count('PRESELECTIONNE'), icon: 'bi-star', tone: 'info' },
      { label: 'Acceptées', value: count('ACCEPTE'), icon: 'bi-check-circle', tone: 'success' },
      { label: 'Refusées', value: count('REFUSE'), icon: 'bi-x-circle', tone: 'danger' },
    ];
  });

  readonly checks = computed<ProfileCheck[]>(() => {
    const p = this.profile();
    if (!p) {
      return [];
    }
    return [
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
      applications: this.applicationService.findMine(),
      jobs: this.jobService.search({ page: 0, size: 3 }),
    }).subscribe({
      next: ({ profile, applications, jobs }) => {
        this.profile.set(profile);
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
