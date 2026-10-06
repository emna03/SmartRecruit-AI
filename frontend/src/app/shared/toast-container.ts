import { Component, inject } from '@angular/core';
import { ToastService } from '../core/services/toast.service';

@Component({
  selector: 'app-toast-container',
  template: `
    <div class="toast-container position-fixed bottom-0 end-0 p-3" style="z-index: 1100">
      @for (toast of toastService.toasts(); track toast.id) {
        <div class="toast show align-items-center border-0 text-bg-{{ toast.type }}" role="alert" aria-live="assertive">
          <div class="d-flex">
            <div class="toast-body">{{ toast.message }}</div>
            <button type="button" class="btn-close btn-close-white me-2 m-auto" aria-label="Fermer"
                    (click)="toastService.dismiss(toast.id)"></button>
          </div>
        </div>
      }
    </div>
  `,
})
export class ToastContainer {
  protected readonly toastService = inject(ToastService);
}
