import { booleanAttribute, ChangeDetectionStrategy, Component, input } from '@angular/core';

export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger' | 'dark';
export type ButtonSize = 'sm' | 'md' | 'lg';

/**
 * Botão do Nexus Design System. Uso: `<button nxButton variant="primary">Salvar</button>`.
 * Durante `loading` o botão fica desabilitado e exibe um indicador de progresso.
 */
@Component({
  selector: 'button[nxButton], a[nxButton]',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'nx-btn',
    '[attr.data-variant]': 'variant()',
    '[attr.data-size]': 'size()',
    '[class.nx-btn--block]': 'block()',
    '[class.nx-btn--loading]': 'loading()',
    '[attr.aria-busy]': 'loading() || null',
    // `disabled` é input (não a propriedade nativa) para não conflitar com o bloqueio durante `loading`.
    '[attr.disabled]': 'loading() || disabled() || null',
  },
  template: `
    @if (loading()) {
      <span class="nx-btn__spinner" aria-hidden="true"></span>
    }
    <span class="nx-btn__label"><ng-content /></span>
  `,
  styleUrl: './button.component.scss',
})
export class ButtonComponent {
  readonly variant = input<ButtonVariant>('primary');
  readonly size = input<ButtonSize>('md');
  readonly block = input<boolean>(false);
  readonly loading = input<boolean>(false);
  readonly disabled = input(false, { transform: booleanAttribute });
}
