import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Campo de formulário: rótulo, controle projetado com ícones opcionais e mensagem de erro/dica.
 *
 * ```html
 * <nx-form-field label="E-mail" for="email" [error]="emailError()">
 *   <nx-icon nxPrefix name="mail" />
 *   <input id="email" class="nx-input" formControlName="email" />
 * </nx-form-field>
 * ```
 */
@Component({
  selector: 'nx-form-field',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'nx-field',
    '[class.nx-field--invalid]': '!!error()',
  },
  template: `
    <div class="nx-field__header">
      <label class="nx-field__label" [attr.for]="for()">{{ label() }}</label>
      <ng-content select="[nxLabelAction]" />
    </div>
    <div class="nx-field__control">
      <span class="nx-field__prefix"><ng-content select="[nxPrefix]" /></span>
      <ng-content />
      <span class="nx-field__suffix"><ng-content select="[nxSuffix]" /></span>
    </div>
    @if (error(); as message) {
      <p class="nx-field__error" [attr.id]="errorId()" role="alert">{{ message }}</p>
    } @else if (hint(); as message) {
      <p class="nx-field__hint">{{ message }}</p>
    }
  `,
  styleUrl: './form-field.component.scss',
})
export class FormFieldComponent {
  readonly label = input.required<string>();
  readonly for = input.required<string>();
  readonly error = input<string | null>(null);
  readonly hint = input<string | null>(null);

  readonly errorId = () => `${this.for()}-error`;
}
