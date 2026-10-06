import { ApplicationStatus, JobOfferStatus, Role } from '../core/models';

/** Couleurs des graphiques, alignées sur les badges de statut et la couleur principale de SmartRecruit AI. */
export const PRIMARY_COLOR = '#4f46e5';

export const APPLICATION_STATUS_COLORS: Record<ApplicationStatus, string> = {
  EN_ATTENTE: '#f59e0b',
  PRESELECTIONNE: '#0ea5e9',
  ACCEPTE: '#16a34a',
  REFUSE: '#dc2626',
};

export const JOB_STATUS_COLORS: Record<JobOfferStatus, string> = {
  BROUILLON: '#94a3b8',
  PUBLIEE: '#16a34a',
  FERMEE: '#475569',
};

export const ROLE_COLORS: Record<Role, string> = {
  ADMIN: '#7c3aed',
  RECRUTEUR: '#0ea5e9',
  CANDIDAT: PRIMARY_COLOR,
};

export const ACTIVITY_COLORS = {
  users: PRIMARY_COLOR,
  offers: '#16a34a',
  applications: '#f59e0b',
};

/** « 2026-10 » → « oct. 2026 ». */
export function monthLabel(month: string): string {
  const [year, value] = month.split('-').map(Number);
  return new Date(Date.UTC(year, value - 1, 1)).toLocaleDateString('fr-FR', {
    month: 'short',
    year: 'numeric',
    timeZone: 'UTC',
  });
}

/** Libellé tronqué pour les axes (le titre complet reste dans l'infobulle et le tableau accessible). */
export function shortLabel(text: string, max = 28): string {
  return text.length > max ? `${text.slice(0, max - 1)}…` : text;
}
