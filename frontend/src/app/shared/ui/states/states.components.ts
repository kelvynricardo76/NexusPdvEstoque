import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { ButtonComponent } from '../button/button.component';
import { IconComponent } from '../icon/icon.component';
import { IconName } from '../icon/icons';

/** Cabeçalho de página com título, subtítulo e ações. */
@Component({
  selector: 'nx-page-header',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="nx-page-header__text">
      <h1 class="nx-page-header__title">{{ title() }}</h1>
      @if (subtitle()) {
        <p class="nx-page-header__subtitle">{{ subtitle() }}</p>
      }
    </div>
    <div class="nx-page-header__actions"><ng-content /></div>
  `,
  styles: `
    :host {
      display: flex;
      flex-wrap: wrap;
      align-items: flex-end;
      justify-content: space-between;
      gap: var(--nx-space-4);
    }
    .nx-page-header__title { font-size: var(--nx-text-2xl); font-weight: 700; }
    .nx-page-header__subtitle { margin-top: var(--nx-space-1); color: var(--nx-text-muted); }
    .nx-page-header__actions { display: flex; flex-wrap: wrap; gap: var(--nx-space-2); }
    .nx-page-header__actions:empty { display: none; }
    @media (max-width: 640px) { .nx-page-header__title { font-size: var(--nx-text-xl); } }
  `,
})
export class PageHeaderComponent {
  readonly title = input.required<string>();
  readonly subtitle = input<string | null>(null);
}

@Component({
  selector: 'nx-empty-state',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <span class="nx-empty__icon"><nx-icon [name]="icon()" [size]="26" /></span>
    <p class="nx-empty__title">{{ title() }}</p>
    @if (message()) {
      <p class="nx-empty__message">{{ message() }}</p>
    }
    <ng-content />
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: var(--nx-space-2);
      padding: var(--nx-space-10) var(--nx-space-4);
      text-align: center;
    }
    .nx-empty__icon {
      display: inline-flex;
      padding: var(--nx-space-3);
      border: 1px solid var(--nx-border-strong);
      border-radius: var(--nx-radius-lg);
      background: var(--nx-surface-2);
      color: var(--nx-text-muted);
    }
    .nx-empty__title { margin-top: var(--nx-space-2); font-weight: 600; }
    .nx-empty__message { max-width: 420px; color: var(--nx-text-muted); font-size: var(--nx-text-sm); }
  `,
})
export class EmptyStateComponent {
  readonly icon = input<IconName>('package');
  readonly title = input.required<string>();
  readonly message = input<string | null>(null);
}

@Component({
  selector: 'nx-error-state',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, ButtonComponent],
  template: `
    <nx-icon name="alert-triangle" [size]="24" />
    <p>{{ message() }}</p>
    <button nxButton variant="secondary" size="sm" type="button" (click)="retry.emit()">
      <nx-icon name="refresh" [size]="14" /> Tentar novamente
    </button>
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: var(--nx-space-3);
      padding: var(--nx-space-8) var(--nx-space-4);
      color: var(--nx-text-muted);
      text-align: center;
    }
    nx-icon { color: var(--nx-danger); }
  `,
})
export class ErrorStateComponent {
  readonly message = input<string>('Não foi possível carregar os dados.');
  readonly retry = output<void>();
}

@Component({
  selector: 'nx-skeleton',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @for (line of lines(); track $index) {
      <span class="nx-skeleton__line" [style.height.px]="height()"></span>
    }
  `,
  styles: `
    :host { display: flex; flex-direction: column; gap: var(--nx-space-3); }
    .nx-skeleton__line {
      display: block;
      border-radius: var(--nx-radius-sm);
      background: linear-gradient(90deg, var(--nx-surface-2) 0%, var(--nx-surface-3) 50%, var(--nx-surface-2) 100%);
      background-size: 200% 100%;
      animation: nx-shimmer 1.4s linear infinite;
    }
    @keyframes nx-shimmer { to { background-position: -200% 0; } }
  `,
})
export class SkeletonComponent {
  readonly rows = input<number>(4);
  readonly height = input<number>(18);

  protected lines(): number[] {
    return Array.from({ length: this.rows() }, (_, index) => index);
  }
}

@Component({
  selector: 'nx-pagination',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <span class="nx-pagination__info">{{ totalElements() }} registro(s)</span>
    @if (totalPages() > 1) {
      <div class="nx-pagination__controls">
        <button type="button" [disabled]="page() === 0" (click)="pageChange.emit(page() - 1)" aria-label="Página anterior">
          <nx-icon name="chevron-left" [size]="16" />
        </button>
        <span>{{ page() + 1 }} / {{ totalPages() }}</span>
        <button
          type="button"
          [disabled]="page() + 1 >= totalPages()"
          (click)="pageChange.emit(page() + 1)"
          aria-label="Próxima página"
        >
          <nx-icon name="chevron-right" [size]="16" />
        </button>
      </div>
    }
  `,
  styles: `
    :host {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: var(--nx-space-3);
      padding: var(--nx-space-3) var(--nx-space-4);
      color: var(--nx-text-muted);
      font-size: var(--nx-text-sm);
    }
    .nx-pagination__controls { display: flex; align-items: center; gap: var(--nx-space-2); }
    button {
      display: inline-flex;
      padding: 6px;
      border: 1px solid var(--nx-border-strong);
      border-radius: var(--nx-radius-sm);
      background: var(--nx-surface-2);
      color: var(--nx-text);
      cursor: pointer;
    }
    button:disabled { opacity: 0.4; cursor: not-allowed; }
  `,
})
export class PaginationComponent {
  readonly page = input.required<number>();
  readonly totalPages = input.required<number>();
  readonly totalElements = input.required<number>();
  readonly pageChange = output<number>();
}
