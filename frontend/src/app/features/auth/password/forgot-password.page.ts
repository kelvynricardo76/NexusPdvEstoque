import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { errorMessage } from '../../../core/util/api-error';
import { AuthLayoutComponent } from '../../../layout/auth-layout/auth-layout.component';
import { AlertComponent } from '../../../shared/ui/alert/alert.component';
import { ButtonComponent } from '../../../shared/ui/button/button.component';
import { FormFieldComponent } from '../../../shared/ui/form-field/form-field.component';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { NexusLogoComponent } from '../../../shared/ui/logo/nexus-logo.component';

/** "Esqueci minha senha" — a resposta é sempre a mesma (não revela e-mails cadastrados). */
@Component({
  selector: 'nx-forgot-password-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, AuthLayoutComponent, AlertComponent, ButtonComponent, FormFieldComponent, IconComponent, NexusLogoComponent],
  template: `
    <nx-auth-layout>
      <header class="login__card-header">
        <nx-logo [size]="52" />
        <div>
          <h2 class="login__card-title">Recuperar acesso</h2>
          <p class="login__card-subtitle">Enviaremos um link para redefinir sua senha</p>
        </div>
      </header>
      @if (sent()) {
        <nx-alert tone="success">
          Se o e-mail estiver cadastrado, você receberá um link para criar uma nova senha em instantes.
        </nx-alert>
        <p class="login__back"><a routerLink="/login">Voltar ao login</a></p>
      } @else {
        <form class="login__form" [formGroup]="form" (ngSubmit)="submit()" novalidate>
          <nx-form-field label="E-mail" for="forgot-email" [error]="submitted() && form.invalid ? 'Informe um e-mail válido.' : null">
            <nx-icon nxPrefix name="mail" [size]="18" />
            <input id="forgot-email" class="nx-input" type="email" formControlName="email" placeholder="seu@email.com" autocomplete="username" />
          </nx-form-field>
          @if (error(); as message) {
            <nx-alert tone="danger">{{ message }}</nx-alert>
          }
          <button nxButton type="submit" variant="dark" size="lg" [block]="true" [loading]="loading()" class="login__submit">
            Enviar link
          </button>
          <p class="login__back"><a routerLink="/login">Voltar ao login</a></p>
        </form>
      }
    </nx-auth-layout>
  `,
})
export class ForgotPasswordPage {
  private readonly http = inject(HttpClient);
  private readonly fb = inject(FormBuilder);

  protected readonly form = this.fb.nonNullable.group({ email: ['', [Validators.required, Validators.email]] });
  protected readonly submitted = signal(false);
  protected readonly loading = signal(false);
  protected readonly sent = signal(false);
  protected readonly error = signal<string | null>(null);

  protected async submit(): Promise<void> {
    this.submitted.set(true);
    if (this.form.invalid) return;
    this.loading.set(true);
    this.error.set(null);
    try {
      await firstValueFrom(this.http.post('/api/auth/forgot-password', this.form.getRawValue()));
      this.sent.set(true);
    } catch (error) {
      this.error.set(errorMessage(error));
    } finally {
      this.loading.set(false);
    }
  }
}
