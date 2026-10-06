import { Routes } from '@angular/router';
import { guestGuard, roleGuard } from './core/auth/auth.guards';
import { SpaceConfig, SpaceLayout } from './layout/space-layout';
import { HomePage } from './features/public/home-page';
import { JobDetailPage } from './features/public/job-detail-page';
import { JobListPage } from './features/public/job-list-page';
import { LoginPage } from './features/public/login-page';
import { NotFoundPage } from './features/public/not-found-page';
import { RegisterPage } from './features/public/register-page';

const CANDIDATE_SPACE: SpaceConfig = {
  title: 'Espace candidat',
  icon: 'bi-person-badge',
  links: [
    { path: '/candidate/dashboard', label: 'Tableau de bord', icon: 'bi-speedometer2' },
    { path: '/candidate/profile', label: 'Mon profil', icon: 'bi-person' },
    { path: '/candidate/cv', label: 'Mon CV', icon: 'bi-file-earmark-person' },
    { path: '/candidate/jobs', label: 'Offres', icon: 'bi-search' },
    { path: '/candidate/applications', label: 'Mes candidatures', icon: 'bi-send' },
    { path: '/candidate/assistant', label: 'Assistant RH', icon: 'bi-chat-dots' },
  ],
};

const RECRUITER_SPACE: SpaceConfig = {
  title: 'Espace recruteur',
  icon: 'bi-briefcase',
  links: [
    { path: '/recruiter/dashboard', label: 'Tableau de bord', icon: 'bi-speedometer2' },
    { path: '/recruiter/company', label: 'Entreprise & profil', icon: 'bi-building' },
    { path: '/recruiter/jobs', label: 'Mes offres', icon: 'bi-list-task' },
    { path: '/recruiter/jobs/create', label: 'Nouvelle offre', icon: 'bi-plus-circle' },
    { path: '/recruiter/turnover', label: 'Risque de turnover', icon: 'bi-graph-up-arrow' },
  ],
};

const ADMIN_SPACE: SpaceConfig = {
  title: 'Administration',
  icon: 'bi-shield-lock',
  links: [
    { path: '/admin/dashboard', label: 'Tableau de bord', icon: 'bi-speedometer2' },
    { path: '/admin/users', label: 'Utilisateurs', icon: 'bi-people' },
    { path: '/admin/companies', label: 'Entreprises', icon: 'bi-building' },
    { path: '/admin/jobs', label: 'Offres', icon: 'bi-list-task' },
    { path: '/admin/turnover', label: 'Risque de turnover', icon: 'bi-graph-up-arrow' },
  ],
};

export const routes: Routes = [
  { path: '', component: HomePage, title: 'SmartRecruit AI — Recrutement intelligent' },
  { path: 'login', component: LoginPage, canActivate: [guestGuard], title: 'Connexion — SmartRecruit AI' },
  { path: 'register', component: RegisterPage, canActivate: [guestGuard], title: 'Inscription — SmartRecruit AI' },
  { path: 'jobs', component: JobListPage, title: "Offres d'emploi — SmartRecruit AI" },
  { path: 'jobs/:id', component: JobDetailPage, title: "Détail de l'offre — SmartRecruit AI" },
  {
    path: 'candidate',
    component: SpaceLayout,
    canActivate: [roleGuard('CANDIDAT')],
    data: { space: CANDIDATE_SPACE },
    loadChildren: () => import('./features/candidate/candidate.routes'),
  },
  {
    path: 'recruiter',
    component: SpaceLayout,
    canActivate: [roleGuard('RECRUTEUR')],
    data: { space: RECRUITER_SPACE },
    loadChildren: () => import('./features/recruiter/recruiter.routes'),
  },
  {
    path: 'admin',
    component: SpaceLayout,
    canActivate: [roleGuard('ADMIN')],
    data: { space: ADMIN_SPACE },
    loadChildren: () => import('./features/admin/admin.routes'),
  },
  { path: '**', component: NotFoundPage, title: 'Page introuvable — SmartRecruit AI' },
];
