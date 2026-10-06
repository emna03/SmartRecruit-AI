import { Routes } from '@angular/router';

/** Espace administrateur minimal (les statistiques détaillées et graphiques viendront à l'étape des dashboards). */
export default [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  {
    path: 'dashboard',
    loadComponent: () => import('./admin-dashboard-page').then((m) => m.AdminDashboardPage),
    title: 'Administration — SmartRecruit AI',
  },
  {
    path: 'users',
    loadComponent: () => import('./admin-users-page').then((m) => m.AdminUsersPage),
    title: 'Utilisateurs — Administration',
  },
  {
    path: 'companies',
    loadComponent: () => import('./admin-companies-page').then((m) => m.AdminCompaniesPage),
    title: 'Entreprises — Administration',
  },
  {
    path: 'jobs',
    loadComponent: () => import('./admin-jobs-page').then((m) => m.AdminJobsPage),
    title: 'Offres — Administration',
  },
  {
    path: 'turnover',
    loadComponent: () => import('../hr/turnover-prediction-page').then((m) => m.TurnoverPredictionPage),
    title: 'Risque de turnover — Administration',
  },
] satisfies Routes;
