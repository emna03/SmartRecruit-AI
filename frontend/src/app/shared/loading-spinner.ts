import { Component, input } from '@angular/core';

@Component({
  selector: 'app-loading-spinner',
  template: `
    <div class="d-flex flex-column align-items-center justify-content-center py-5 text-secondary" role="status">
      <div class="spinner-border text-primary mb-2"></div>
      <span class="small">{{ label() }}</span>
    </div>
  `,
})
export class LoadingSpinner {
  readonly label = input('Chargement...');
}
