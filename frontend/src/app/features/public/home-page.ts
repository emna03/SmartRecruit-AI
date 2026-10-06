import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { errorMessage } from '../../core/http-errors';
import { JobOffer } from '../../core/models';
import { JobOfferService } from '../../core/services/job-offer.service';
import { JobCard } from '../../shared/job-card';
import { LoadingSpinner } from '../../shared/loading-spinner';

@Component({
  selector: 'app-home-page',
  imports: [FormsModule, RouterLink, JobCard, LoadingSpinner],
  templateUrl: './home-page.html',
  styles: `
    .hero {
      background: radial-gradient(1200px 400px at 10% -10%, rgba(124, 58, 237, 0.25), transparent),
        linear-gradient(135deg, #312e81 0%, #4f46e5 55%, #7c3aed 100%);
      color: #fff;
    }
    .hero .search-box {
      background: #fff;
      border-radius: 1rem;
      box-shadow: 0 20px 40px rgba(17, 24, 39, 0.25);
    }
    .feature-icon {
      width: 3rem;
      height: 3rem;
      border-radius: 0.9rem;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      font-size: 1.4rem;
      background: var(--sr-primary-soft);
      color: var(--sr-primary);
    }
  `,
})
export class HomePage implements OnInit {
  private readonly jobService = inject(JobOfferService);
  private readonly router = inject(Router);
  protected readonly auth = inject(AuthService);

  readonly latestJobs = signal<JobOffer[]>([]);
  readonly totalJobs = signal<number | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  keyword = '';
  location = '';

  ngOnInit(): void {
    this.jobService.search({ page: 0, size: 6 }).subscribe({
      next: (page) => {
        this.latestJobs.set(page.content);
        this.totalJobs.set(page.totalElements);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(errorMessage(err, 'Impossible de charger les offres.'));
        this.loading.set(false);
      },
    });
  }

  search(): void {
    const queryParams: Record<string, string> = {};
    if (this.keyword.trim()) queryParams['keyword'] = this.keyword.trim();
    if (this.location.trim()) queryParams['location'] = this.location.trim();
    void this.router.navigate(['/jobs'], { queryParams });
  }
}
