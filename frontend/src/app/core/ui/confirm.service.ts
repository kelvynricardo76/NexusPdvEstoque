import { Injectable, signal } from '@angular/core';

export interface ConfirmOptions {
  title: string;
  message: string;
  confirmText?: string;
  tone?: 'danger' | 'primary';
  /** Exige que o usuário informe um motivo (ex.: cancelamento de venda). */
  requireReason?: boolean;
  reasonLabel?: string;
}

export interface ConfirmResult {
  confirmed: boolean;
  reason?: string;
}

interface PendingConfirm extends ConfirmOptions {
  resolve: (result: ConfirmResult) => void;
}

/** Confirmação obrigatória para ações destrutivas. */
@Injectable({ providedIn: 'root' })
export class ConfirmService {
  readonly pending = signal<PendingConfirm | null>(null);

  ask(options: ConfirmOptions): Promise<ConfirmResult> {
    return new Promise((resolve) => this.pending.set({ ...options, resolve }));
  }

  async confirm(options: ConfirmOptions): Promise<boolean> {
    return (await this.ask(options)).confirmed;
  }

  close(result: ConfirmResult): void {
    const current = this.pending();
    this.pending.set(null);
    current?.resolve(result);
  }
}
