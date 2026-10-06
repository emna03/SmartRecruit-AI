import { Injectable, signal } from '@angular/core';

export type ToastType = 'success' | 'danger' | 'info' | 'warning';

export interface Toast {
  id: number;
  type: ToastType;
  message: string;
}

/** Notifications courtes affichées en bas à droite (succès d'une action, erreur réseau...). */
@Injectable({ providedIn: 'root' })
export class ToastService {
  private nextId = 1;
  readonly toasts = signal<Toast[]>([]);

  success(message: string): void {
    this.show('success', message);
  }

  error(message: string): void {
    this.show('danger', message, 6000);
  }

  info(message: string): void {
    this.show('info', message);
  }

  dismiss(id: number): void {
    this.toasts.update((list) => list.filter((t) => t.id !== id));
  }

  private show(type: ToastType, message: string, durationMs = 4000): void {
    const id = this.nextId++;
    this.toasts.update((list) => [...list, { id, type, message }]);
    setTimeout(() => this.dismiss(id), durationMs);
  }
}
