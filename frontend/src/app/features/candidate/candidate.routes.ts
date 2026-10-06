import { Routes } from '@angular/router';
import { JobDetailPage } from '../public/job-detail-page';
import { JobListPage } from '../public/job-list-page';

export default [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  {
    path: 'dashboard',
    loadComponent: () => import('./candidate-dashboard-page').then((m) => m.CandidateDashboardPage),
    title: 'Tableau de bord — Espace candidat',
  },
  {
    path: 'profile',
    loadComponent: () => import('./profile/candidate-profile-page').then((m) => m.CandidateProfilePage),
    title: 'Mon profil — Espace candidat',
  },
  {
    path: 'cv',
    loadComponent: () => import('./cv/candidate-cv-page').then((m) => m.CandidateCvPage),
    title: 'Mon CV — Espace candidat',
  },
  {
    path: 'jobs',
    component: JobListPage,
    data: { detailBase: '/candidate/jobs', embedded: true },
    title: 'Offres — Espace candidat',
  },
  {
    path: 'jobs/:id',
    component: JobDetailPage,
    data: { detailBase: '/candidate/jobs' },
    title: "Détail de l'offre — Espace candidat",
  },
  {
    path: 'applications',
    loadComponent: () => import('./candidate-applications-page').then((m) => m.CandidateApplicationsPage),
    title: 'Mes candidatures — Espace candidat',
  },
  {
    path: 'assistant',
    loadComponent: () => import('./chatbot/chatbot-page').then((m) => m.ChatbotPage),
    title: 'Assistant RH — Espace candidat',
  },
] satisfies Routes;
