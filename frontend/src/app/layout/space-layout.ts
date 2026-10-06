import { Component, input } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

export interface SpaceLink {
  path: string;
  label: string;
  icon: string;
}

export interface SpaceConfig {
  title: string;
  icon: string;
  links: SpaceLink[];
}

/** Mise en page des espaces connectés : barre latérale (écran large) ou menu horizontal (mobile). */
@Component({
  selector: 'app-space-layout',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <div class="container-xl py-4">
      <div class="row g-4">
        <aside class="col-lg-3">
          <div class="card sidebar">
            <div class="card-body p-2">
              <div class="px-2 pt-1 pb-2 small text-uppercase fw-semibold text-secondary d-none d-lg-block">
                <i aria-hidden="true" class="bi {{ space().icon }} me-1"></i>{{ space().title }}
              </div>
              <nav class="nav nav-pills flex-lg-column flex-nowrap overflow-auto gap-1">
                @for (link of space().links; track link.path) {
                  <a class="nav-link text-nowrap" [routerLink]="link.path" routerLinkActive="active">
                    <i aria-hidden="true" class="bi {{ link.icon }} me-2"></i>{{ link.label }}
                  </a>
                }
              </nav>
            </div>
          </div>
        </aside>
        <section class="col-lg-9">
          <router-outlet />
        </section>
      </div>
    </div>
  `,
  styles: `
    .sidebar .nav-link {
      color: #374151;
      border-radius: 0.6rem;
    }
    .sidebar .nav-link.active {
      background: var(--sr-primary);
      color: #fff;
    }
    @media (min-width: 992px) {
      .sidebar {
        position: sticky;
        top: 5rem;
      }
    }
  `,
})
export class SpaceLayout {
  /** Fourni par les data de la route (liaison automatique withComponentInputBinding). */
  readonly space = input.required<SpaceConfig>();
}
