import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Role } from '../models';
import { AuthService } from './auth.service';

/**
 * Autorise uniquement les rôles indiqués. Non connecté : redirection vers /login (avec returnUrl) ;
 * mauvais rôle : redirection vers l'espace de son propre rôle.
 * Le backend applique les mêmes règles : ce guard n'est qu'un confort de navigation.
 */
export function roleGuard(...roles: Role[]): CanActivateFn {
  return (_route, state) => {
    const auth = inject(AuthService);
    const router = inject(Router);
    if (!auth.isLoggedIn()) {
      return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
    }
    const role = auth.role();
    return role && roles.includes(role) ? true : router.createUrlTree([auth.homeFor(role)]);
  };
}

/** Pages de connexion / inscription : un utilisateur déjà connecté est renvoyé vers son espace. */
export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.isLoggedIn() ? inject(Router).createUrlTree([auth.homeFor()]) : true;
};
