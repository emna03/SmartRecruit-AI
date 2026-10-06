import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-no-company-notice',
  imports: [RouterLink],
  template: `
    <div class="card border-primary-subtle" data-testid="no-company">
      <div class="empty-state">
        <i aria-hidden="true" class="bi bi-building-add text-primary"></i>
        <p class="fw-semibold text-body mb-1">Votre entreprise n'est pas encore renseignée</p>
        <p class="mb-3">Créez-la pour pouvoir publier des offres d'emploi.</p>
        <a class="btn btn-primary" routerLink="/recruiter/company">Créer mon entreprise</a>
      </div>
    </div>
  `,
})
export class NoCompanyNotice {}
