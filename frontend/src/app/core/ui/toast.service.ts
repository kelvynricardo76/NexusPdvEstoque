import { Injectable, signal } from '@angular/core';

export type ToastTone = 'success' | 'danger' | 'info' | 'warning';

export interface Toast {
  readonly id: number;
  readonly tone: ToastTone;
  readonly message: string;
}

/** Feedback rápido de ações (sucesso/erro). */
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
    this.toasts.update((list) => list.filter((toast) => toast.id !== id));
  }

  private show(tone: ToastTone, message: string, duration = 4000): void {
    const toast: Toast = { id: this.nextId++, tone, message };
    this.toasts.update((list) => [...list.slice(-3), toast]);
    setTimeout(() => this.dismiss(toast.id), duration);
  }
}
