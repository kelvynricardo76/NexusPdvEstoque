import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { SessionService } from '../../../core/auth/session.service';
import { NavigationService } from '../../../core/navigation/navigation.service';
import { errorMessage } from '../../../core/util/api-error';
import { AuthLayoutComponent } from '../../../layout/auth-layout/auth-layout.component';
import { AlertComponent } from '../../../shared/ui/alert/alert.component';
import { ButtonComponent } from '../../../shared/ui/button/button.component';
import { FormFieldComponent } from '../../../shared/ui/form-field/form-field.component';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { NexusLogoComponent } from '../../../shared/ui/logo/nexus-logo.component';

const USERNAME = /^[A-Za-z0-9._-]{3,50}$/;

/** Aceita e-mail válido ou nome de usuário simples (letras, números, ponto, hífen, sublinhado). */
function emailOrUsername(control: AbstractControl): ValidationErrors | null {
  const value = String(control.value ?? '').trim();
  if (!value || USERNAME.test(value)) return null;
  return Validators.email(control);
}

/**
 * Login do tenant e do Super Admin (rota com {@code data.platform = true}).
 * A sessão é criada no servidor (cookie HttpOnly); nenhum token é guardado no navegador.
 */
@Component({
  selector: 'nx-login-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    AuthLayoutComponent,
    AlertComponent,
    ButtonComponent,
    FormFieldComponent,
    IconComponent,
    NexusLogoComponent,
  ],
  templateUrl: './login.page.html',
})
export class LoginPage {
  private readonly fb = inject(FormBuilder);
  private readonly session = inject(SessionService);
  private readonly navigation = inject(NavigationService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly platform = this.route.snapshot.data['platform'] === true;

  protected readonly form = this.fb.nonNullable.group({
    // Tenant: e-mail ou usuário (ex.: login de atalho "admin" no ambiente local). Super Admin: só e-mail.
    email: ['', [Validators.required, this.platform ? Validators.email : emailOrUsername, Validators.maxLength(254)]],
    password: ['', [Validators.required, Validators.maxLength(128)]],
  });

  protected readonly passwordVisible = signal(false);
  protected readonly submitted = signal(false);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(
    this.route.snapshot.queryParamMap.get('reset') === 'ok' ? 'Senha redefinida. Entre com a nova senha.' : null,
  );

  // Reavalia as mensagens a cada evento do formulário (valor, status, touched).
  private readonly formEvents = toSignal(this.form.events);

  protected readonly emailError = computed(() => {
    this.formEvents();
    if (!this.submitted()) return null;
    const control = this.form.controls.email;
    if (control.hasError('required')) return this.platform ? 'Informe seu e-mail.' : 'Informe seu e-mail ou usuário.';
    if (control.hasError('email') || control.hasError('maxlength')) {
      return this.platform ? 'Informe um e-mail válido.' : 'Informe um e-mail ou usuário válido.';
    }
    return null;
  });

  protected readonly passwordError = computed(() => {
    this.formEvents();
    if (!this.submitted()) return null;
    return this.form.controls.password.hasError('required') ? 'Informe sua senha.' : null;
  });

  protected togglePasswordVisibility(): void {
    this.passwordVisible.update((visible) => !visible);
  }

  protected async submit(): Promise<void> {
    this.submitted.set(true);
    this.error.set(null);
    this.notice.set(null);
    this.form.markAllAsTouched();
    if (this.form.invalid || this.loading()) return;

    const { email, password } = this.form.getRawValue();
    this.loading.set(true);
    try {
      if (this.platform) {
        await this.session.loginPlatform(email, password);
        await this.router.navigateByUrl(this.safeReturnUrl('/super-admin') ?? '/super-admin/dashboard');
      } else {
        const me = await this.session.login(email, password);
        const target = me.operable ? (this.safeReturnUrl('/') ?? this.navigation.landingRoute()) : '/bloqueado';
        await this.router.navigateByUrl(target);
      }
    } catch (error) {
      this.error.set(errorMessage(error));
    } finally {
      this.loading.set(false);
    }
  }

  /** Aceita apenas caminhos internos da área correta (evita open redirect). */
  private safeReturnUrl(area: '/' | '/super-admin'): string | null {
    const url = this.route.snapshot.queryParamMap.get('returnUrl');
    if (!url || !url.startsWith('/') || url.startsWith('//') || url.includes('login')) return null;
    const isPlatformUrl = url.startsWith('/super-admin');
    return (area === '/super-admin') === isPlatformUrl ? url : null;
  }
}
