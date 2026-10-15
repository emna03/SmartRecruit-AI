import { Routes } from '@angular/router';

export default [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  {
    path: 'dashboard',
    loadComponent: () => import('./recruiter-dashboard-page').then((m) => m.RecruiterDashboardPage),
    title: 'Tableau de bord — Espace recruteur',
  },
  {
    path: 'company',
    loadComponent: () => import('./recruiter-company-page').then((m) => m.RecruiterCompanyPage),
    title: 'Entreprise & profil — Espace recruteur',
  },
  {
    path: 'jobs',
    loadComponent: () => import('./recruiter-jobs-page').then((m) => m.RecruiterJobsPage),
    title: 'Mes offres — Espace recruteur',
  },
  {
    path: 'jobs/create',
    loadComponent: () => import('./job-form-page').then((m) => m.JobFormPage),
    title: 'Nouvelle offre — Espace recruteur',
  },
  {
    path: 'jobs/:id/edit',
    loadComponent: () => import('./job-form-page').then((m) => m.JobFormPage),
    title: "Modifier l'offre — Espace recruteur",
  },
  {
    path: 'jobs/:id/candidates',
    loadComponent: () => import('./job-candidates-page').then((m) => m.JobCandidatesPage),
    title: 'Candidats — Espace recruteur',
  },
  {
    path: 'applications/:id',
    loadComponent: () => import('./application-detail-page').then((m) => m.ApplicationDetailPage),
    title: 'Candidature — Espace recruteur',
  },
  {
    path: 'turnover',
    loadComponent: () => import('../hr/turnover-prediction-page').then((m) => m.TurnoverPredictionPage),
    title: 'Risque de turnover — Espace recruteur',
  },
  {
    path: 'clustering',
    loadComponent: () => import('../admin/clustering-page').then((m) => m.ClusteringPage),
    title: 'Clustering K-Means — Espace recruteur',
  },
] satisfies Routes;
