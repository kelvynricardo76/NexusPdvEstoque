import { DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { SessionService } from '../../core/auth/session.service';
import { limitText, USER_STATUS } from '../../core/i18n/labels';
import { Page, Role, UserDetail, UserSummary } from '../../core/models/api.models';
import { ConfirmService } from '../../core/ui/confirm.service';
import { ToastService } from '../../core/ui/toast.service';
import { errorMessage } from '../../core/util/api-error';
import { params } from '../../core/util/http-params';
import { AlertComponent } from '../../shared/ui/alert/alert.component';
import { BadgeComponent } from '../../shared/ui/badge/badge.component';
import { ButtonComponent } from '../../shared/ui/button/button.component';
import { IconComponent } from '../../shared/ui/icon/icon.component';
import { ModalComponent } from '../../shared/ui/modal/modal.component';
import {
  EmptyStateComponent,
  ErrorStateComponent,
  PageHeaderComponent,
  PaginationComponent,
  SkeletonComponent,
} from '../../shared/ui/states/states.components';

@Component({
  selector: 'nx-users-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, FormsModule, ReactiveFormsModule, RouterLink, AlertComponent, BadgeComponent, ButtonComponent, IconComponent, ModalComponent, EmptyStateComponent, ErrorStateComponent, PageHeaderComponent, PaginationComponent, SkeletonComponent],
  template: `
    <div class="nx-page">
      <nx-page-header title="Usuários e permissões" [subtitle]="'Funcionários da empresa • limite do plano: ' + userLimit()">
        <a nxButton variant="secondary" routerLink="/usuarios/cargos"><nx-icon name="shield-check" [size]="16" /> Cargos</a>
        @if (session.hasPermission('USER_CREATE')) {
          <button nxButton type="button" (click)="open(null)"><nx-icon name="plus" [size]="16" /> Novo funcionário</button>
        }
      </nx-page-header>

      <div class="nx-toolbar">
        <div class="nx-search">
          <nx-icon name="search" [size]="16" />
          <input class="nx-control" type="search" placeholder="Nome ou e-mail" [ngModel]="query()" (ngModelChange)="query.set($event)" (keydown.enter)="reload()" />
        </div>
        <select class="nx-control" [ngModel]="status()" (ngModelChange)="status.set($event); reload()" aria-label="Status">
          <option value="">Todos</option><option value="ACTIVE">Ativos</option><option value="INACTIVE">Inativos</option>
        </select>
      </div>

      <div class="nx-table-wrap">
        @if (error(); as message) {
          <nx-error-state [message]="message" (retry)="load()" />
        } @else if (loading() && !page()) {
          <div style="padding: 1rem"><nx-skeleton [rows]="6" /></div>
        } @else if (page(); as p) {
          @if (p.content.length) {
            <table class="nx-table nx-table--stack">
              <thead><tr><th>Nome</th><th>E-mail</th><th>Cargo</th><th>Status</th><th>Último acesso</th><th></th></tr></thead>
              <tbody>
                @for (user of p.content; track user.id) {
                  <tr>
                    <td class="nx-stack-main nx-table__main">{{ user.name }} @if (user.id === session.me()?.user?.id) { <small class="text-muted">(você)</small> }</td>
                    <td data-label="E-mail">{{ user.email }}</td>
                    <td data-label="Cargo">{{ user.roleName }}</td>
                    <td><nx-badge [tone]="statuses[user.status].tone">{{ statuses[user.status].label }}</nx-badge></td>
                    <td data-label="Último acesso">{{ user.lastLoginAt ? (user.lastLoginAt | date: 'dd/MM/yyyy HH:mm') : 'Nunca' }}</td>
                    <td class="nx-table__actions">
                      @if (user.id !== session.me()?.user?.id) {
                        @if (session.hasPermission('USER_UPDATE')) {
                          <button type="button" class="nx-icon-button" (click)="open(user)" aria-label="Editar" title="Editar"><nx-icon name="edit" [size]="16" /></button>
                        }
                        @if (session.hasPermission('USER_PERMISSION_MANAGE') && user.roleCode !== 'TENANT_ADMIN') {
                          <a class="nx-icon-button" [routerLink]="['/usuarios', user.id, 'permissoes']" aria-label="Permissões" title="Permissões individuais"><nx-icon name="key" [size]="16" /></a>
                        }
                        @if (session.hasPermission('USER_UPDATE') && user.status === 'ACTIVE') {
                          <button type="button" class="nx-icon-button" (click)="resetPassword(user)" aria-label="Redefinir senha" title="Enviar redefinição de senha"><nx-icon name="lock" [size]="16" /></button>
                        }
                        @if (session.hasPermission('USER_DISABLE')) {
                          <button type="button" class="nx-icon-button" [class.is-danger]="user.status === 'ACTIVE'" (click)="toggle(user)"
                                  [attr.aria-label]="user.status === 'ACTIVE' ? 'Desativar' : 'Ativar'" [title]="user.status === 'ACTIVE' ? 'Desativar' : 'Ativar'">
                            <nx-icon [name]="user.status === 'ACTIVE' ? 'power' : 'check-circle'" [size]="16" />
                          </button>
                        }
                      }
                    </td>
                  </tr>
                }
              </tbody>
            </table>
            <nx-pagination [page]="p.page" [totalPages]="p.totalPages" [totalElements]="p.totalElements" (pageChange)="goTo($event)" />
          } @else {
            <nx-empty-state icon="users" title="Nenhum usuário encontrado" />
          }
        }
      </div>
    </div>

    @if (editing() !== undefined) {
      <nx-modal [title]="editing() ? 'Editar funcionário' : 'Novo funcionário'" (closed)="editing.set(undefined)">
        <form id="user-form" class="nx-form-grid" [formGroup]="form" (ngSubmit)="save()" novalidate>
          <div class="nx-form-grid__full">
            <label class="nx-label" for="u-name">Nome *</label>
            <input id="u-name" class="nx-control" formControlName="name" maxlength="120" />
          </div>
          <div>
            <label class="nx-label" for="u-email">E-mail *</label>
            <input id="u-email" class="nx-control" type="email" formControlName="email" maxlength="254" />
          </div>
          <div>
            <label class="nx-label" for="u-phone">Telefone</label>
            <input id="u-phone" class="nx-control" formControlName="phone" maxlength="30" />
          </div>
          <div class="nx-form-grid__full">
            <label class="nx-label" for="u-role">Cargo *</label>
            <select id="u-role" class="nx-control" formControlName="roleId">
              @for (role of roles(); track role.id) {
                <option [value]="role.id">{{ role.name }}{{ role.description ? ' — ' + role.description : '' }}</option>
              }
            </select>
            <p class="nx-hint">Permissões adicionais ou restrições individuais podem ser definidas depois.</p>
          </div>
          @if (!editing()) {
            <div class="nx-form-grid__full">
              <label class="nx-label" for="u-password">Senha inicial</label>
              <input id="u-password" class="nx-control" type="password" formControlName="password" autocomplete="new-password" />
              <p class="nx-hint">Deixe em branco para enviar ao funcionário um link para definir a própria senha.</p>
            </div>
          }
          @if (formError(); as message) { <nx-alert class="nx-form-grid__full" tone="danger">{{ message }}</nx-alert> }
        </form>
        <ng-container modalFooter>
          <button nxButton variant="secondary" type="button" (click)="editing.set(undefined)">Cancelar</button>
          <button nxButton type="submit" form="user-form" [loading]="saving()">Salvar</button>
        </ng-container>
      </nx-modal>
    }
  `,
})
export class UsersPage implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly fb = inject(FormBuilder);
  private readonly toast = inject(ToastService);
  private readonly confirm = inject(ConfirmService);
  protected readonly session = inject(SessionService);
  protected readonly statuses = USER_STATUS;

  protected readonly query = signal('');
  protected readonly status = signal('');
  protected readonly pageIndex = signal(0);
  protected readonly page = signal<Page<UserSummary> | null>(null);
  protected readonly roles = signal<Role[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly editing = signal<UserSummary | null | undefined>(undefined);
  protected readonly saving = signal(false);
  protected readonly formError = signal<string | null>(null);
  protected readonly userLimit = computed(() => limitText(this.session.me()?.limits['MAX_USERS']) + ' ativos');

  protected readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(120)]],
    email: ['', [Validators.required, Validators.email]],
    phone: [''],
    roleId: ['', Validators.required],
    password: [''],
  });

  ngOnInit(): void {
    this.load();
    this.http.get<Role[]>('/api/roles').subscribe({ next: (roles) => this.roles.set(roles), error: () => undefined });
  }

  protected reload(): void {
    this.pageIndex.set(0);
    this.load();
  }

  protected goTo(page: number): void {
    this.pageIndex.set(page);
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.http
      .get<Page<UserSummary>>('/api/users', {
        params: params({ q: this.query().trim(), status: this.status(), page: this.pageIndex(), size: 20 }),
      })
      .subscribe({
        next: (page) => {
          this.page.set(page);
          this.loading.set(false);
        },
        error: (error) => {
          this.error.set(errorMessage(error));
          this.loading.set(false);
        },
      });
  }

  protected open(user: UserSummary | null): void {
    this.formError.set(null);
    const defaultRole = this.roles().find((role) => role.code === 'CASHIER') ?? this.roles()[0];
    this.form.reset({
      name: user?.name ?? '',
      email: user?.email ?? '',
      phone: user?.phone ?? '',
      roleId: user?.roleId ?? defaultRole?.id ?? '',
      password: '',
    });
    if (user) this.form.controls.email.disable();
    else this.form.controls.email.enable();
    this.editing.set(user);
  }

  protected save(): void {
    if (this.form.invalid) {
      this.formError.set('Verifique nome, e-mail e cargo.');
      return;
    }
    const value = this.form.getRawValue();
    const current = this.editing();
    this.saving.set(true);
    const call = current
      ? this.http.put<UserDetail>(`/api/users/${current.id}`, { name: value.name, phone: value.phone || null, roleId: value.roleId })
      : this.http.post<UserDetail>('/api/users', {
          name: value.name,
          email: value.email,
          phone: value.phone || null,
          roleId: value.roleId,
          password: value.password || null,
        });
    call.subscribe({
      next: () => {
        this.saving.set(false);
        this.editing.set(undefined);
        this.toast.success(current ? 'Funcionário atualizado.' : value.password ? 'Funcionário criado.' : 'Funcionário criado. Um link para definir a senha foi enviado.');
        this.load();
      },
      error: (error) => {
        this.saving.set(false);
        this.formError.set(errorMessage(error));
      },
    });
  }

  protected async toggle(user: UserSummary): Promise<void> {
    const deactivate = user.status === 'ACTIVE';
    if (deactivate && !(await this.confirm.confirm({
      title: 'Desativar funcionário',
      message: `${user.name} perderá o acesso imediatamente.`,
      confirmText: 'Desativar',
      tone: 'danger',
    }))) return;
    this.http.put(`/api/users/${user.id}/status`, { active: !deactivate }).subscribe({
      next: () => {
        this.toast.success(deactivate ? 'Funcionário desativado.' : 'Funcionário reativado.');
        this.load();
      },
      error: (error) => this.toast.error(errorMessage(error)),
    });
  }

  protected async resetPassword(user: UserSummary): Promise<void> {
    if (!(await this.confirm.confirm({
      title: 'Redefinir senha',
      message: `Enviar para ${user.email} um link para criar uma nova senha?`,
      confirmText: 'Enviar link',
    }))) return;
    this.http.post(`/api/users/${user.id}/password-reset`, {}).subscribe({
      next: () => this.toast.success('Link de redefinição enviado.'),
      error: (error) => this.toast.error(errorMessage(error)),
    });
  }
}
