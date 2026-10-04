import { DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { SessionService } from '../../../core/auth/session.service';
import { USER_STATUS } from '../../../core/i18n/labels';
import { PlatformAdminView } from '../../../core/models/super-admin.models';
import { ConfirmService } from '../../../core/ui/confirm.service';
import { ToastService } from '../../../core/ui/toast.service';
import { errorMessage } from '../../../core/util/api-error';
import { AlertComponent } from '../../../shared/ui/alert/alert.component';
import { BadgeComponent } from '../../../shared/ui/badge/badge.component';
import { ButtonComponent } from '../../../shared/ui/button/button.component';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { ModalComponent } from '../../../shared/ui/modal/modal.component';
import { PageHeaderComponent } from '../../../shared/ui/states/states.components';

@Component({
  selector: 'nx-sa-admins-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, ReactiveFormsModule, AlertComponent, BadgeComponent, ButtonComponent, IconComponent, ModalComponent, PageHeaderComponent],
  template: `
    <div class="nx-page">
      <nx-page-header title="Administradores" subtitle="Equipe Nexus Development com acesso ao painel">
        <button nxButton type="button" (click)="creating.set(true)"><nx-icon name="plus" [size]="16" /> Novo administrador</button>
      </nx-page-header>
      <section class="nx-table-wrap">
        <table class="nx-table nx-table--stack">
          <thead><tr><th>Nome</th><th>E-mail</th><th>Status</th><th>Último acesso</th><th></th></tr></thead>
          <tbody>
            @for (admin of admins(); track admin.id) {
              <tr>
                <td class="nx-stack-main nx-table__main">{{ admin.name }}</td>
                <td data-label="E-mail">{{ admin.email }}</td>
                <td><nx-badge [tone]="statuses[admin.status].tone">{{ statuses[admin.status].label }}</nx-badge></td>
                <td data-label="Último acesso">{{ admin.lastLoginAt ? (admin.lastLoginAt | date: 'dd/MM/yyyy HH:mm') : 'Nunca' }}</td>
                <td class="nx-table__actions">
                  @if (admin.id !== session.platformMe()?.id) {
                    <button nxButton size="sm" variant="ghost" type="button" (click)="toggle(admin)">{{ admin.status === 'ACTIVE' ? 'Desativar' : 'Ativar' }}</button>
                  }
                </td>
              </tr>
            }
          </tbody>
        </table>
      </section>
    </div>
    @if (creating()) {
      <nx-modal title="Novo administrador" size="sm" (closed)="creating.set(false)">
        <form id="admin-form" [formGroup]="form" (ngSubmit)="create()" class="nx-form-grid">
          <div class="nx-form-grid__full"><label class="nx-label" for="ad-name">Nome</label><input id="ad-name" class="nx-control" formControlName="name" /></div>
          <div class="nx-form-grid__full"><label class="nx-label" for="ad-email">E-mail</label><input id="ad-email" class="nx-control" type="email" formControlName="email" /></div>
          <div class="nx-form-grid__full"><label class="nx-label" for="ad-pass">Senha</label><input id="ad-pass" class="nx-control" type="password" formControlName="password" autocomplete="new-password" /></div>
          @if (error(); as message) { <nx-alert class="nx-form-grid__full" tone="danger">{{ message }}</nx-alert> }
        </form>
        <ng-container modalFooter>
          <button nxButton variant="secondary" type="button" (click)="creating.set(false)">Cancelar</button>
          <button nxButton type="submit" form="admin-form">Criar</button>
        </ng-container>
      </nx-modal>
    }
  `,
})
export class SaAdminsPage implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly fb = inject(FormBuilder);
  private readonly toast = inject(ToastService);
  private readonly confirm = inject(ConfirmService);
  protected readonly session = inject(SessionService);
  protected readonly statuses = USER_STATUS;
  protected readonly admins = signal<PlatformAdminView[]>([]);
  protected readonly creating = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly form = this.fb.nonNullable.group({
    name: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]],
  });

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.http.get<PlatformAdminView[]>('/api/super-admin/admins').subscribe({ next: (list) => this.admins.set(list), error: () => undefined });
  }

  protected create(): void {
    if (this.form.invalid) {
      this.error.set('Preencha nome, e-mail e senha (mínimo 8 caracteres, letras e números).');
      return;
    }
    this.http.post('/api/super-admin/admins', this.form.getRawValue()).subscribe({
      next: () => {
        this.creating.set(false);
        this.form.reset();
        this.toast.success('Administrador criado.');
        this.load();
      },
      error: (error) => this.error.set(errorMessage(error)),
    });
  }

  protected async toggle(admin: PlatformAdminView): Promise<void> {
    const deactivate = admin.status === 'ACTIVE';
    if (deactivate && !(await this.confirm.confirm({ title: 'Desativar administrador', message: `${admin.name} perderá o acesso ao painel.`, confirmText: 'Desativar', tone: 'danger' }))) return;
    this.http.put(`/api/super-admin/admins/${admin.id}/status`, { active: !deactivate }).subscribe({
      next: () => this.load(),
      error: (error) => this.toast.error(errorMessage(error)),
    });
  }
}
