import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Superfície padrão de conteúdo (cards de KPI, painéis, formulários). */
@Component({
  selector: 'nx-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'nx-card',
    '[attr.data-padding]': 'padding()',
    '[class.nx-card--interactive]': 'interactive()',
  },
  template: `<ng-content />`,
  styles: `
    :host {
      display: block;
      padding: var(--nx-space-5);
      border: 1px solid var(--nx-border);
      border-radius: var(--nx-radius-lg);
      background: var(--nx-surface);
      box-shadow: var(--nx-shadow-sm);
    }

    :host([data-padding='none']) {
      padding: 0;
    }

    :host([data-padding='lg']) {
      padding: var(--nx-space-8);
    }

    :host(.nx-card--interactive) {
      transition:
        border-color var(--nx-duration) var(--nx-ease),
        box-shadow var(--nx-duration) var(--nx-ease);
    }

    :host(.nx-card--interactive:hover) {
      border-color: var(--nx-primary-line);
      box-shadow: var(--nx-glow);
    }
  `,
})
export class CardComponent {
  readonly padding = input<'none' | 'md' | 'lg'>('md');
  readonly interactive = input<boolean>(false);
}
