import { HttpErrorResponse } from '@angular/common/http';
import { FormGroup } from '@angular/forms';
import { ApiError } from './models';

/** Message lisible pour l'utilisateur à partir d'une erreur HTTP (format ApiError du backend). */
export function errorMessage(error: unknown, fallback = 'Une erreur inattendue est survenue.'): string {
  if (!(error instanceof HttpErrorResponse)) {
    return fallback;
  }
  if (error.status === 0) {
    return 'Impossible de joindre le serveur. Vérifiez votre connexion ou que le backend est démarré.';
  }
  const body = error.error as Partial<ApiError> | null;
  if (body && typeof body === 'object' && typeof body.message === 'string' && body.message) {
    return body.message;
  }
  if (error.status >= 500) {
    return 'Erreur interne du serveur. Réessayez plus tard.';
  }
  return fallback;
}

/**
 * Reporte les erreurs de validation du backend (fieldErrors) sur les champs du formulaire.
 * Renvoie true si au moins un champ a été marqué.
 */
export function applyFieldErrors(form: FormGroup, error: unknown): boolean {
  if (!(error instanceof HttpErrorResponse)) {
    return false;
  }
  const fieldErrors = (error.error as Partial<ApiError> | null)?.fieldErrors;
  if (!fieldErrors) {
    return false;
  }
  let applied = false;
  for (const [field, message] of Object.entries(fieldErrors)) {
    const control = form.get(field);
    if (control) {
      control.setErrors({ ...control.errors, server: message });
      control.markAsTouched();
      applied = true;
    }
  }
  return applied;
}

/** Chaîne vide ou espaces -> null (le backend traite null comme « non renseigné »). */
export function emptyToNull(value: string | null | undefined): string | null {
  const trimmed = value?.trim();
  return trimmed ? trimmed : null;
}
