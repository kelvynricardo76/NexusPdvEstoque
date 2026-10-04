import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ToastService } from '../../../core/ui/toast.service';
import { IconComponent } from '../icon/icon.component';

@Component({
  selector: 'nx-toast-container',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    @for (toast of toasts.toasts(); track toast.id) {
      <div class="nx-toast" [attr.data-tone]="toast.tone" role="status">
        <nx-icon [name]="toast.tone === 'success' ? 'check-circle' : toast.tone === 'danger' ? 'alert-circle' : 'info'" [size]="18" />
        <span class="nx-toast__message">{{ toast.message }}</span>
        <button type="button" (click)="toasts.dismiss(toast.id)" aria-label="Fechar notificação">
          <nx-icon name="x" [size]="14" />
        </button>
      </div>
    }
  `,
  styles: `
    :host {
      position: fixed;
      right: var(--nx-space-4);
      bottom: var(--nx-space-4);
      z-index: 200;
      display: flex;
      flex-direction: column;
      gap: var(--nx-space-2);
      max-width: min(420px, calc(100vw - 2rem));
    }
    .nx-toast {
      --tone: var(--nx-info);
      display: flex;
      align-items: center;
      gap: var(--nx-space-3);
      padding: var(--nx-space-3) var(--nx-space-4);
      border: 1px solid color-mix(in srgb, var(--tone) 40%, transparent);
      border-left: 3px solid var(--tone);
      border-radius: var(--nx-radius-md);
      background: var(--nx-surface-2);
      box-shadow: var(--nx-shadow-md);
      font-size: var(--nx-text-sm);
      animation: nx-toast-in 180ms var(--nx-ease);
    }
    .nx-toast[data-tone='success'] { --tone: var(--nx-primary); }
    .nx-toast[data-tone='danger'] { --tone: var(--nx-danger); }
    .nx-toast[data-tone='warning'] { --tone: var(--nx-warning); }
    nx-icon { color: var(--tone); }
    .nx-toast__message { flex: 1; }
    button { display: inline-flex; padding: 4px; border: 0; background: transparent; color: var(--nx-text-muted); cursor: pointer; }
    @keyframes nx-toast-in { from { opacity: 0; transform: translateY(8px); } }
  `,
})
export class ToastContainerComponent {
  protected readonly toasts = inject(ToastService);
}
