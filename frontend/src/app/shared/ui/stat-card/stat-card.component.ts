import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { IconComponent } from '../icon/icon.component';
import { IconName } from '../icon/icons';

/** Card de indicador (KPI) do dashboard. */
@Component({
  selector: 'nx-stat-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, DecimalPipe],
  template: `
    <div class="nx-stat__head">
      <span class="nx-stat__icon"><nx-icon [name]="icon()" [size]="20" /></span>
      <span class="nx-stat__label">{{ label() }}</span>
    </div>
    <p class="nx-stat__value">{{ value() }}</p>
    @if (variation() !== null && variation() !== undefined) {
      <p class="nx-stat__hint" [class.is-up]="variation()! >= 0" [class.is-down]="variation()! < 0">
        <nx-icon [name]="variation()! >= 0 ? 'trending-up' : 'trending-down'" [size]="14" />
        {{ variation()! | number: '1.0-1' }}% {{ hint() }}
      </p>
    } @else if (hint()) {
      <p class="nx-stat__hint" [attr.data-tone]="hintTone()">{{ hint() }}</p>
    }
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: var(--nx-space-2);
      padding: var(--nx-space-5);
      border: 1px solid var(--nx-border);
      border-radius: var(--nx-radius-lg);
      background: var(--nx-surface);
      min-width: 0;
    }
    .nx-stat__head { display: flex; align-items: center; gap: var(--nx-space-3); }
    .nx-stat__icon {
      display: inline-flex;
      padding: 8px;
      border: 1px solid var(--nx-primary-line);
      border-radius: var(--nx-radius-md);
      background: var(--nx-primary-soft);
      color: var(--nx-primary);
    }
    .nx-stat__label { color: var(--nx-text-muted); font-size: var(--nx-text-sm); font-weight: 500; }
    .nx-stat__value {
      font-family: var(--nx-font-display);
      font-size: 1.6rem;
      font-weight: 700;
      letter-spacing: -0.02em;
      font-variant-numeric: tabular-nums;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .nx-stat__hint { display: flex; align-items: center; gap: 4px; color: var(--nx-text-muted); font-size: var(--nx-text-xs); }
    .nx-stat__hint.is-up { color: var(--nx-primary); }
    .nx-stat__hint.is-down { color: var(--nx-danger); }
    .nx-stat__hint[data-tone='warning'] { color: var(--nx-warning); }
    .nx-stat__hint[data-tone='danger'] { color: var(--nx-danger); }
    @media (max-width: 639px) {
      :host { padding: var(--nx-space-4); }
      .nx-stat__value { font-size: 1.2rem; }
      .nx-stat__icon { padding: 6px; }
    }
  `,
})
export class StatCardComponent {
  readonly icon = input.required<IconName>();
  readonly label = input.required<string>();
  readonly value = input.required<string>();
  readonly variation = input<number | null | undefined>(null);
  readonly hint = input<string | null>(null);
  readonly hintTone = input<'muted' | 'warning' | 'danger'>('muted');
}
