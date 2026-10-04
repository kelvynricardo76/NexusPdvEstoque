import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { errorMessage } from '../../../core/util/api-error';
import { AuthLayoutComponent } from '../../../layout/auth-layout/auth-layout.component';
import { AlertComponent } from '../../../shared/ui/alert/alert.component';
import { ButtonComponent } from '../../../shared/ui/button/button.component';
import { FormFieldComponent } from '../../../shared/ui/form-field/form-field.component';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { NexusLogoComponent } from '../../../shared/ui/logo/nexus-logo.component';

/** Definição de nova senha a partir do link (token de uso único). */
@Component({
  selector: 'nx-reset-password-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, AuthLayoutComponent, AlertComponent, ButtonComponent, FormFieldComponent, IconComponent, NexusLogoComponent],
  template: `
    <nx-auth-layout>
      <header class="login__card-header">
        <nx-logo [size]="52" />
        <div>
          <h2 class="login__card-title">Nova senha</h2>
          <p class="login__card-subtitle">Mínimo de 8 caracteres, com letras e números</p>
        </div>
      </header>
      @if (!token) {
        <nx-alert tone="danger">Link inválido. Solicite uma nova redefinição.</nx-alert>
        <p class="login__back"><a routerLink="/esqueci-senha">Solicitar novo link</a></p>
      } @else {
        <form class="login__form" [formGroup]="form" (ngSubmit)="submit()" novalidate>
          <nx-form-field label="Nova senha" for="reset-password">
            <nx-icon nxPrefix name="lock" [size]="18" />
            <input id="reset-password" class="nx-input" type="password" formControlName="password" autocomplete="new-password" />
          </nx-form-field>
          <nx-form-field label="Confirme a senha" for="reset-confirm" [error]="mismatch() ? 'As senhas não conferem.' : null">
            <nx-icon nxPrefix name="lock" [size]="18" />
            <input id="reset-confirm" class="nx-input" type="password" formControlName="confirm" autocomplete="new-password" />
          </nx-form-field>
          @if (error(); as message) {
            <nx-alert tone="danger">{{ message }}</nx-alert>
          }
          <button nxButton type="submit" variant="dark" size="lg" [block]="true" [loading]="loading()" class="login__submit">
            Salvar nova senha
          </button>
        </form>
      }
    </nx-auth-layout>
  `,
})
export class ResetPasswordPage {
  private readonly http = inject(HttpClient);
  private readonly fb = inject(FormBuilder);
  private readonly router = inject(Router);

  protected readonly token = inject(ActivatedRoute).snapshot.queryParamMap.get('token');
  protected readonly form = this.fb.nonNullable.group({
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(128)]],
    confirm: ['', [Validators.required]],
  });
  protected readonly loading = signal(false);
  protected readonly mismatch = signal(false);
  protected readonly error = signal<string | null>(null);

  protected async submit(): Promise<void> {
    const { password, confirm } = this.form.getRawValue();
    this.mismatch.set(password !== confirm);
    if (this.form.invalid || this.mismatch()) {
      this.error.set(this.form.invalid ? 'A senha deve ter entre 8 e 128 caracteres.' : null);
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    try {
      await firstValueFrom(this.http.post('/api/auth/reset-password', { token: this.token, newPassword: password }));
      await this.router.navigate(['/login'], { queryParams: { reset: 'ok' } });
    } catch (error) {
      this.error.set(errorMessage(error));
    } finally {
      this.loading.set(false);
    }
  }
}
