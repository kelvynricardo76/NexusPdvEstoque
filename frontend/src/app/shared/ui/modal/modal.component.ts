import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  HostListener,
  inject,
  input,
  output,
} from '@angular/core';
import { IconComponent } from '../icon/icon.component';

/** Diálogo modal acessível (ESC fecha, foco inicial no primeiro campo). */
@Component({
  selector: 'nx-modal',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <div class="nx-modal__backdrop" (click)="closed.emit()"></div>
    <section
      class="nx-modal__panel"
      role="dialog"
      aria-modal="true"
      [attr.aria-label]="title()"
      [attr.data-size]="size()"
    >
      <header class="nx-modal__header">
        <div>
          <h2 class="nx-modal__title">{{ title() }}</h2>
          @if (subtitle()) {
            <p class="nx-modal__subtitle">{{ subtitle() }}</p>
          }
        </div>
        <button type="button" class="nx-modal__close" (click)="closed.emit()" aria-label="Fechar">
          <nx-icon name="x" [size]="18" />
        </button>
      </header>
      <div class="nx-modal__body"><ng-content /></div>
      <footer class="nx-modal__footer"><ng-content select="[modalFooter]" /></footer>
    </section>
  `,
  styles: `
    :host {
      position: fixed;
      inset: 0;
      z-index: 100;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: var(--nx-space-4);
    }
    .nx-modal__backdrop {
      position: absolute;
      inset: 0;
      background: rgba(3, 6, 5, 0.72);
      backdrop-filter: blur(3px);
    }
    .nx-modal__panel {
      position: relative;
      display: flex;
      flex-direction: column;
      width: 100%;
      max-width: 560px;
      max-height: calc(100dvh - 2rem);
      border: 1px solid var(--nx-border-strong);
      border-radius: var(--nx-radius-xl);
      background: var(--nx-surface);
      box-shadow: 0 24px 64px rgba(0, 0, 0, 0.5);
    }
    .nx-modal__panel[data-size='lg'] { max-width: 860px; }
    .nx-modal__panel[data-size='sm'] { max-width: 420px; }
    .nx-modal__header {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: var(--nx-space-4);
      padding: var(--nx-space-5) var(--nx-space-6);
      border-bottom: 1px solid var(--nx-border);
    }
    .nx-modal__title { font-size: var(--nx-text-lg); }
    .nx-modal__subtitle { margin-top: 2px; color: var(--nx-text-muted); font-size: var(--nx-text-sm); }
    .nx-modal__close {
      display: inline-flex;
      padding: 6px;
      border: 0;
      border-radius: var(--nx-radius-sm);
      background: transparent;
      color: var(--nx-text-muted);
      cursor: pointer;
    }
    .nx-modal__close:hover { background: var(--nx-surface-3); color: var(--nx-text); }
    .nx-modal__body { padding: var(--nx-space-6); overflow-y: auto; }
    .nx-modal__footer {
      display: flex;
      justify-content: flex-end;
      gap: var(--nx-space-3);
      padding: var(--nx-space-4) var(--nx-space-6);
      border-top: 1px solid var(--nx-border);
    }
    .nx-modal__footer:empty { display: none; }
  `,
})
export class ModalComponent implements AfterViewInit {
  private readonly host = inject(ElementRef<HTMLElement>);

  readonly title = input.required<string>();
  readonly subtitle = input<string | null>(null);
  readonly size = input<'sm' | 'md' | 'lg'>('md');
  readonly closed = output<void>();

  @HostListener('document:keydown.escape')
  protected onEscape(): void {
    this.closed.emit();
  }

  ngAfterViewInit(): void {
    const element = this.host.nativeElement as HTMLElement;
    const focusable = element.querySelector<HTMLElement>('input, select, textarea, button:not(.nx-modal__close)');
    focusable?.focus();
  }
}
