import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { IconComponent } from '../icon/icon.component';
import { IconName } from '../icon/icons';

export type AlertTone = 'info' | 'success' | 'warning' | 'danger';

const TONE_ICON: Record<AlertTone, IconName> = {
  info: 'info',
  success: 'check',
  warning: 'alert-circle',
  danger: 'alert-circle',
};

/** Mensagem contextual inline (erros de formulário, avisos, confirmações). */
@Component({
  selector: 'nx-alert',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  host: {
    class: 'nx-alert',
    '[attr.data-tone]': 'tone()',
    '[attr.role]': 'tone() === "danger" ? "alert" : "status"',
  },
  template: `
    <nx-icon [name]="icon()" [size]="18" />
    <div class="nx-alert__body"><ng-content /></div>
  `,
  styles: `
    :host {
      --alert-fg: var(--nx-info);
      --alert-bg: var(--nx-info-soft);

      display: flex;
      align-items: flex-start;
      gap: var(--nx-space-3);
      padding: var(--nx-space-3) var(--nx-space-4);
      border: 1px solid color-mix(in srgb, var(--alert-fg) 30%, transparent);
      border-radius: var(--nx-radius-md);
      background: var(--alert-bg);
      color: var(--nx-text);
      font-size: var(--nx-text-sm);
    }

    nx-icon { color: var(--alert-fg); margin-top: 1px; }
    .nx-alert__body { flex: 1; min-width: 0; }

    :host([data-tone='success']) { --alert-fg: var(--nx-success); --alert-bg: var(--nx-success-soft); }
    :host([data-tone='warning']) { --alert-fg: var(--nx-warning); --alert-bg: var(--nx-warning-soft); }
    :host([data-tone='danger'])  { --alert-fg: var(--nx-danger);  --alert-bg: var(--nx-danger-soft); }
  `,
})
export class AlertComponent {
  readonly tone = input<AlertTone>('info');

  protected readonly icon = computed(() => TONE_ICON[this.tone()]);
}
