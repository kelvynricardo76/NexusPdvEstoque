import { ChangeDetectionStrategy, Component, input } from '@angular/core';

export type BadgeTone = 'neutral' | 'primary' | 'success' | 'warning' | 'danger' | 'info';

/** Selo de status (ex.: Ativo, Baixo, Sem estoque). */
@Component({
  selector: 'nx-badge',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'nx-badge',
    '[attr.data-tone]': 'tone()',
  },
  template: `<ng-content />`,
  styles: `
    :host {
      --badge-fg: var(--nx-text-muted);
      --badge-bg: var(--nx-surface-3);

      display: inline-flex;
      align-items: center;
      gap: var(--nx-space-1);
      height: 24px;
      padding: 0 var(--nx-space-2);
      border-radius: var(--nx-radius-sm);
      background: var(--badge-bg);
      color: var(--badge-fg);
      font-size: var(--nx-text-xs);
      font-weight: 600;
      white-space: nowrap;
    }

    :host([data-tone='primary']) { --badge-fg: var(--nx-primary); --badge-bg: var(--nx-primary-soft); }
    :host([data-tone='success']) { --badge-fg: var(--nx-success); --badge-bg: var(--nx-success-soft); }
    :host([data-tone='warning']) { --badge-fg: var(--nx-warning); --badge-bg: var(--nx-warning-soft); }
    :host([data-tone='danger'])  { --badge-fg: var(--nx-danger);  --badge-bg: var(--nx-danger-soft); }
    :host([data-tone='info'])    { --badge-fg: var(--nx-info);    --badge-bg: var(--nx-info-soft); }
  `,
})
export class BadgeComponent {
  readonly tone = input<BadgeTone>('neutral');
}
