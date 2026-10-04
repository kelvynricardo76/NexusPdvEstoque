import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { SessionService } from '../../core/auth/session.service';
import { ToastService } from '../../core/ui/toast.service';
import { errorMessage } from '../../core/util/api-error';
import { AlertComponent } from '../../shared/ui/alert/alert.component';
import { ButtonComponent } from '../../shared/ui/button/button.component';

/** Dados do próprio usuário e troca de senha (exige a senha atual). */
@Component({
  selector: 'nx-account-settings',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, AlertComponent, ButtonComponent],
  template: `
    <div class="nx-grid nx-grid--2">
      <section class="nx-panel">
        <h2 class="nx-panel__title">Meus dados</h2>
        <dl class="nx-definition">
          <dt>Nome</dt><dd>{{ session.me()?.user?.name }}</dd>
          <dt>E-mail</dt><dd>{{ session.me()?.user?.email }}</dd>
          <dt>Cargo</dt><dd>{{ session.me()?.user?.role?.name }}</dd>
          <dt>Empresa</dt><dd>{{ session.me()?.tenant?.tradeName }}</dd>
        </dl>
      </section>
      <form class="nx-panel" [formGroup]="form" (ngSubmit)="save()" novalidate>
        <h2 class="nx-panel__title">Alterar senha</h2>
        <div>
          <label class="nx-label" for="a-current">Senha atual</label>
          <input id="a-current" class="nx-control" type="password" formControlName="currentPassword" autocomplete="current-password" />
        </div>
        <div>
          <label class="nx-label" for="a-new">Nova senha</label>
          <input id="a-new" class="nx-control" type="password" formControlName="newPassword" autocomplete="new-password" />
          <p class="nx-hint">Mínimo de 8 caracteres, com letras e números.</p>
        </div>
        <div>
          <label class="nx-label" for="a-confirm">Confirme a nova senha</label>
          <input id="a-confirm" class="nx-control" type="password" formControlName="confirm" autocomplete="new-password" />
        </div>
        @if (error(); as message) { <nx-alert tone="danger">{{ message }}</nx-alert> }
        <div class="nx-actions"><button nxButton type="submit" [loading]="saving()">Alterar senha</button></div>
      </form>
    </div>
  `,
})
export class AccountSettingsComponent {
  private readonly http = inject(HttpClient);
  private readonly fb = inject(FormBuilder);
  private readonly toast = inject(ToastService);
  protected readonly session = inject(SessionService);

  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly form = this.fb.nonNullable.group({
    currentPassword: ['', Validators.required],
    newPassword: ['', [Validators.required, Validators.minLength(8)]],
    confirm: ['', Validators.required],
  });

  protected save(): void {
    const value = this.form.getRawValue();
    if (this.form.invalid) {
      this.error.set('Preencha as senhas (mínimo de 8 caracteres).');
      return;
    }
    if (value.newPassword !== value.confirm) {
      this.error.set('A confirmação não confere com a nova senha.');
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    this.http.post('/api/auth/change-password', { currentPassword: value.currentPassword, newPassword: value.newPassword }).subscribe({
      next: () => {
        this.saving.set(false);
        this.form.reset();
        this.toast.success('Senha alterada.');
      },
      error: (error) => {
        this.saving.set(false);
        this.error.set(errorMessage(error));
      },
    });
  }
}
