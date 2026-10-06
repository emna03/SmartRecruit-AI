import { Component, computed, input, output } from '@angular/core';

/** Pagination (pages numérotées à partir de 0, comme le backend). */
@Component({
  selector: 'app-pagination',
  template: `
    @if (totalPages() > 1) {
      <nav aria-label="Pagination">
        <ul class="pagination justify-content-center mb-0">
          <li class="page-item" [class.disabled]="page() === 0">
            <button class="page-link" type="button" (click)="go(page() - 1)" aria-label="Page précédente">
              <i aria-hidden="true" class="bi bi-chevron-left"></i>
            </button>
          </li>
          @for (p of pages(); track p) {
            <li class="page-item" [class.active]="p === page()">
              <button class="page-link" type="button" (click)="go(p)">{{ p + 1 }}</button>
            </li>
          }
          <li class="page-item" [class.disabled]="page() >= totalPages() - 1">
            <button class="page-link" type="button" (click)="go(page() + 1)" aria-label="Page suivante">
              <i aria-hidden="true" class="bi bi-chevron-right"></i>
            </button>
          </li>
        </ul>
      </nav>
    }
  `,
})
export class Pagination {
  readonly page = input.required<number>();
  readonly totalPages = input.required<number>();
  readonly pageChange = output<number>();

  /** Au plus 7 numéros de page autour de la page courante. */
  readonly pages = computed(() => {
    const total = this.totalPages();
    const start = Math.max(0, Math.min(this.page() - 3, total - 7));
    const end = Math.min(total, start + 7);
    return Array.from({ length: end - start }, (_, i) => start + i);
  });

  go(target: number): void {
    if (target >= 0 && target < this.totalPages() && target !== this.page()) {
      this.pageChange.emit(target);
    }
  }
}
