import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ConfirmService } from '../../../core/ui/confirm.service';
import { ButtonComponent } from '../button/button.component';
import { ModalComponent } from '../modal/modal.component';

@Component({
  selector: 'nx-confirm-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, ButtonComponent, ModalComponent],
  template: `
    @if (confirm.pending(); as request) {
      <nx-modal [title]="request.title" size="sm" (closed)="cancel()">
        <p class="nx-confirm__message">{{ request.message }}</p>
        @if (request.requireReason) {
          <label class="nx-label" for="confirm-reason">{{ request.reasonLabel ?? 'Motivo' }}</label>
          <textarea
            id="confirm-reason"
            class="nx-control"
            rows="3"
            maxlength="300"
            [ngModel]="reason()"
            (ngModelChange)="reason.set($event)"
          ></textarea>
        }
        <ng-container modalFooter>
          <button nxButton variant="secondary" type="button" (click)="cancel()">Voltar</button>
          <button
            nxButton
            type="button"
            [variant]="request.tone === 'danger' ? 'danger' : 'primary'"
            [disabled]="request.requireReason && reason().trim().length < 3"
            (click)="accept()"
          >
            {{ request.confirmText ?? 'Confirmar' }}
          </button>
        </ng-container>
      </nx-modal>
    }
  `,
  styles: `
    .nx-confirm__message { margin-bottom: var(--nx-space-4); color: var(--nx-text-muted); }
    textarea { width: 100%; resize: vertical; }
  `,
})
export class ConfirmDialogComponent {
  protected readonly confirm = inject(ConfirmService);
  protected readonly reason = signal('');

  protected accept(): void {
    const reason = this.reason().trim();
    this.reason.set('');
    this.confirm.close({ confirmed: true, reason: reason || undefined });
  }

  protected cancel(): void {
    this.reason.set('');
    this.confirm.close({ confirmed: false });
  }
}
