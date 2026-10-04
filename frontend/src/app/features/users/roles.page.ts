import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { SessionService } from '../../core/auth/session.service';
import { PermissionInfo, Role } from '../../core/models/api.models';
import { ConfirmService } from '../../core/ui/confirm.service';
import { ToastService } from '../../core/ui/toast.service';
import { errorMessage } from '../../core/util/api-error';
import { AlertComponent } from '../../shared/ui/alert/alert.component';
import { BadgeComponent } from '../../shared/ui/badge/badge.component';
import { ButtonComponent } from '../../shared/ui/button/button.component';
import { IconComponent } from '../../shared/ui/icon/icon.component';
import { ModalComponent } from '../../shared/ui/modal/modal.component';
import { ErrorStateComponent, PageHeaderComponent, SkeletonComponent } from '../../shared/ui/states/states.components';
import { PermissionMatrixComponent } from './permission-matrix.component';

/** Cargos padrão e personalizados (conjuntos reutilizáveis de permissões). */
@Component({
  selector: 'nx-roles-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink, AlertComponent, BadgeComponent, ButtonComponent, IconComponent, ModalComponent, ErrorStateComponent, PageHeaderComponent, SkeletonComponent, PermissionMatrixComponent],
  template: `
    <div class="nx-page">
      <nx-page-header title="Cargos e permissões" subtitle="Defina o que cada função pode fazer no sistema">
        <a nxButton variant="secondary" routerLink="/usuarios"><nx-icon name="arrow-left" [size]="16" /> Usuários</a>
        @if (canManage) {
          <button nxButton type="button" (click)="open(null)"><nx-icon name="plus" [size]="16" /> Novo cargo</button>
        }
      </nx-page-header>

      @if (error(); as message) {
        <nx-error-state [message]="message" (retry)="load()" />
      } @else if (loading()) {
        <nx-skeleton [rows]="5" />
      } @else {
        <div class="nx-grid nx-grid--3">
          @for (role of roles(); track role.id) {
            <article class="nx-panel">
              <div class="nx-panel__header">
                <h2 class="nx-panel__title">{{ role.name }}</h2>
                @if (role.systemRole) { <nx-badge tone="primary">Sistema</nx-badge> }
                @else if (!role.code) { <nx-badge tone="info">Personalizado</nx-badge> }
              </div>
              <p class="text-muted text-small">{{ role.description || 'Sem descrição' }}</p>
              <p class="text-small">{{ role.systemRole ? 'Todas as permissões contratadas' : role.permissions.length + ' permissão(ões)' }} • {{ role.userCount }} usuário(s)</p>
              <div class="nx-actions">
                <button nxButton variant="secondary" size="sm" type="button" (click)="open(role)">
                  {{ role.systemRole || !canManage ? 'Visualizar' : 'Editar' }}
                </button>
                @if (canManage && !role.code) {
                  <button nxButton variant="ghost" size="sm" type="button" (click)="remove(role)">Excluir</button>
                }
              </div>
            </article>
          }
        </div>
      }
    </div>

    @if (editing() !== undefined) {
      <nx-modal [title]="editing()?.name ?? 'Novo cargo'" size="lg" (closed)="editing.set(undefined)">
        <div class="nx-form-grid" style="margin-bottom: 1rem">
          <div>
            <label class="nx-label" for="r-name">Nome do cargo *</label>
            <input id="r-name" class="nx-control" maxlength="80" [ngModel]="name()" (ngModelChange)="name.set($event)" [disabled]="readonly()" />
          </div>
          <div>
            <label class="nx-label" for="r-desc">Descrição</label>
            <input id="r-desc" class="nx-control" maxlength="300" [ngModel]="description()" (ngModelChange)="description.set($event)" [disabled]="readonly()" />
          </div>
        </div>
        @if (editing()?.systemRole) {
          <nx-alert tone="info">O Administrador possui todas as permissões dos módulos contratados e não pode ser alterado.</nx-alert>
        } @else {
          <nx-permission-matrix [catalog]="catalog()" [selected]="selected()" [readonly]="readonly()" (selectedChange)="selected.set($event)" />
        }
        @if (formError(); as message) { <nx-alert tone="danger" style="margin-top: 1rem">{{ message }}</nx-alert> }
        <ng-container modalFooter>
          <button nxButton variant="secondary" type="button" (click)="editing.set(undefined)">Fechar</button>
          @if (!readonly()) {
            <button nxButton type="button" [loading]="saving()" (click)="save()">Salvar cargo</button>
          }
        </ng-container>
      </nx-modal>
    }
  `,
})
export class RolesPage implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly toast = inject(ToastService);
  private readonly confirm = inject(ConfirmService);
  private readonly session = inject(SessionService);

  protected readonly canManage = this.session.hasPermission('USER_PERMISSION_MANAGE');
  protected readonly roles = signal<Role[]>([]);
  protected readonly catalog = signal<PermissionInfo[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly editing = signal<Role | null | undefined>(undefined);
  protected readonly readonly = signal(false);
  protected readonly name = signal('');
  protected readonly description = signal('');
  protected readonly selected = signal<Set<string>>(new Set());
  protected readonly saving = signal(false);
  protected readonly formError = signal<string | null>(null);

  ngOnInit(): void {
    this.load();
    this.http.get<PermissionInfo[]>('/api/permissions').subscribe({ next: (list) => this.catalog.set(list), error: () => undefined });
  }

  protected load(): void {
    this.error.set(null);
    this.http.get<Role[]>('/api/roles').subscribe({
      next: (roles) => {
        this.roles.set(roles);
        this.loading.set(false);
      },
      error: (error) => {
        this.error.set(errorMessage(error));
        this.loading.set(false);
      },
    });
  }

  protected open(role: Role | null): void {
    this.formError.set(null);
    this.name.set(role?.name ?? '');
    this.description.set(role?.description ?? '');
    this.selected.set(new Set(role?.permissions ?? []));
    this.readonly.set(!this.canManage || !!role?.systemRole);
    this.editing.set(role);
  }

  protected save(): void {
    if (this.name().trim().length < 2) {
      this.formError.set('Informe o nome do cargo.');
      return;
    }
    const body = { name: this.name().trim(), description: this.description().trim() || null, permissions: [...this.selected()] };
    const current = this.editing();
    this.saving.set(true);
    const call = current ? this.http.put<Role>(`/api/roles/${current.id}`, body) : this.http.post<Role>('/api/roles', body);
    call.subscribe({
      next: () => {
        this.saving.set(false);
        this.editing.set(undefined);
        this.toast.success('Cargo salvo. As permissões valem a partir da próxima ação dos usuários.');
        this.load();
      },
      error: (error) => {
        this.saving.set(false);
        this.formError.set(errorMessage(error));
      },
    });
  }

  protected async remove(role: Role): Promise<void> {
    if (!(await this.confirm.confirm({
      title: 'Excluir cargo',
      message: `Excluir o cargo "${role.name}"? Só é possível quando nenhum usuário o utiliza.`,
      confirmText: 'Excluir',
      tone: 'danger',
    }))) return;
    this.http.delete(`/api/roles/${role.id}`).subscribe({
      next: () => {
        this.toast.success('Cargo excluído.');
        this.load();
      },
      error: (error) => this.toast.error(errorMessage(error)),
    });
  }
}
