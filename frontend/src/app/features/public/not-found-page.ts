import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-not-found-page',
  imports: [RouterLink],
  template: `
    <div class="container py-5 text-center">
      <div class="display-1 fw-bold text-primary">404</div>
      <h1 class="h4 mb-3">Page introuvable</h1>
      <p class="text-secondary mb-4">La page demandée n'existe pas ou a été déplacée.</p>
      <a class="btn btn-primary" routerLink="/">Retour à l'accueil</a>
    </div>
  `,
})
export class NotFoundPage {}
