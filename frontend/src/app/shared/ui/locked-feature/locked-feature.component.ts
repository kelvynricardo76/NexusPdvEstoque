import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ButtonComponent } from '../button/button.component';
import { IconComponent } from '../icon/icon.component';

/**
 * Recurso não contratado (UX de upgrade) — exibido apenas para o administrador da empresa.
 * Funcionários comuns simplesmente não veem o recurso.
 */
@Component({
  selector: 'nx-locked-feature',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, ButtonComponent, IconComponent],
  template: `
    <span class="nx-locked__icon"><nx-icon name="lock" [size]="18" /></span>
    <div class="nx-locked__text">
      <strong>{{ title() }}</strong>
      <span>Disponível no plano {{ planName() }}</span>
    </div>
    <a nxButton variant="secondary" size="sm" [routerLink]="['/upgrade']" [queryParams]="{ feature: feature() }">
      Conhecer {{ planName() }}
    </a>
  `,
  styles: `
    :host {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: var(--nx-space-4);
      padding: var(--nx-space-5);
      border: 1px dashed var(--nx-primary-line);
      border-radius: var(--nx-radius-lg);
      background: linear-gradient(135deg, rgba(163, 230, 53, 0.05), transparent 60%), var(--nx-surface);
    }
    .nx-locked__icon {
      display: inline-flex;
      padding: 10px;
      border-radius: var(--nx-radius-md);
      background: var(--nx-primary-soft);
      color: var(--nx-primary);
    }
    .nx-locked__text { display: flex; flex: 1; flex-direction: column; min-width: 180px; }
    .nx-locked__text span { color: var(--nx-text-muted); font-size: var(--nx-text-sm); }
  `,
})
export class LockedFeatureComponent {
  readonly title = input.required<string>();
  readonly feature = input.required<string>();
  readonly planName = input<string>('Plus');
}
