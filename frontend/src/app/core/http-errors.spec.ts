import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, FormGroup } from '@angular/forms';
import { applyFieldErrors, emptyToNull, errorMessage } from './http-errors';

describe('http-errors', () => {
  it('uses the backend message', () => {
    const error = new HttpErrorResponse({ status: 409, error: { status: 409, message: 'Vous avez déjà postulé à cette offre' } });
    expect(errorMessage(error)).toBe('Vous avez déjà postulé à cette offre');
  });

  it('explains when the server is unreachable', () => {
    expect(errorMessage(new HttpErrorResponse({ status: 0 }))).toContain('Impossible de joindre le serveur');
  });

  it('falls back for server errors without a body', () => {
    expect(errorMessage(new HttpErrorResponse({ status: 500 }))).toContain('Erreur interne');
    expect(errorMessage(new Error('x'), 'Par défaut')).toBe('Par défaut');
  });

  it('copies backend fieldErrors onto the form controls', () => {
    const form = new FormGroup({ email: new FormControl('x'), password: new FormControl('y') });
    const error = new HttpErrorResponse({
      status: 400,
      error: { message: 'Données invalides', fieldErrors: { email: 'Email invalide', unknown: 'ignored' } },
    });

    expect(applyFieldErrors(form, error)).toBe(true);
    expect(form.controls.email.errors).toEqual({ server: 'Email invalide' });
    expect(form.controls.email.touched).toBe(true);
    expect(form.controls.password.errors).toBeNull();
  });

  it('turns blank strings into null', () => {
    expect(emptyToNull('  ')).toBeNull();
    expect(emptyToNull(undefined)).toBeNull();
    expect(emptyToNull(' Tunis ')).toBe('Tunis');
  });
});
