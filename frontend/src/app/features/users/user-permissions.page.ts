import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, input, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { PermissionEffect, PermissionInfo, Role, UserDetail } from '../../core/models/api.models';
import { ToastService } from '../../core/ui/toast.service';
import { errorMessage } from '../../core/util/api-error';
import { AlertComponent } from '../../shared/ui/alert/alert.component';
import { BadgeComponent } from '../../shared/ui/badge/badge.component';
import { ButtonComponent } from '../../shared/ui/button/button.component';
import { IconComponent } from '../../shared/ui/icon/icon.component';
import { ErrorStateComponent, PageHeaderComponent, SkeletonComponent } from '../../shared/ui/states/states.components';

type Choice = 'ROLE' | PermissionEffect;

/**
 * Permissões individuais: além do cargo, o administrador pode PERMITIR (ALLOW) ou NEGAR (DENY)
 * permissões específicas. Regra: DENY sempre vence; módulos não contratados nunca têm efeito.
 */
@Component({
  selector: 'nx-user-permissions-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink, AlertComponent, BadgeComponent, ButtonComponent, IconComponent, ErrorStateComponent, PageHeaderComponent, SkeletonComponent],
  template: `
    <div class="nx-page">
      @if (error(); as message) {
        <nx-error-state [message]="message" (retry)="load()" />
      } @else if (!detail()) {
        <nx-skeleton [rows]="8" />
      } @else {
        <nx-page-header [title]="'Permissões de ' + detail()!.user.name" [subtitle]="'Cargo: ' + detail()!.user.roleName">
          <a nxButton variant="secondary" routerLink="/usuarios"><nx-icon name="arrow-left" [size]="16" /> Voltar</a>
          <button nxButton type="button" [loading]="saving()" (click)="save()">Salvar permissões</button>
        </nx-page-header>
        <nx-alert tone="info">
          "Padrão do cargo" segue o cargo do funcionário. Use <strong>Permitir</strong> para conceder algo a mais e
          <strong>Negar</strong> para restringir — negar sempre prevalece.
        </nx-alert>
        <div class="nx-table-wrap"><div class="nx-table-scroll">
          <table class="nx-table nx-table--stack">
            <thead><tr><th>Permissão</th><th>Módulo</th><th>Cargo concede?</th><th>Ajuste individual</th><th>Resultado</th></tr></thead>
            <tbody>
              @for (permission of catalog(); track permission.code) {
                <tr>
                  <td class="nx-stack-main nx-table__main">{{ permission.label }}</td>
                  <td data-label="Módulo">{{ permission.moduleLabel }}</td>
                  <td data-label="Cargo">{{ roleGrants().has(permission.code) ? 'Sim' : 'Não' }}</td>
                  <td data-label="Ajuste">
                    @if (permission.available) {
                      <select class="nx-control choice" [ngModel]="choices()[permission.code] ?? 'ROLE'"
                              (ngModelChange)="setChoice(permission.code, $event)" [attr.aria-label]="'Ajuste de ' + permission.label">
                        <option value="ROLE">Padrão do cargo</option>
                        <option value="ALLOW">Permitir</option>
                        <option value="DENY">Negar</option>
                      </select>
                    } @else {
                      <span class="text-muted text-small"><nx-icon name="lock" [size]="12" /> Não contratado</span>
                    }
                  </td>
                  <td>
                    @if (effective(permission)) { <nx-badge tone="success">Permitido</nx-badge> }
                    @else { <nx-badge tone="neutral">Sem acesso</nx-badge> }
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div></div>
      }
    </div>
  `,
  styles: `.choice { width: 170px; height: 34px; }`,
})
export class UserPermissionsPage implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);

  readonly id = input.required<string>();
  protected readonly detail = signal<UserDetail | null>(null);
  protected readonly catalog = signal<PermissionInfo[]>([]);
  protected readonly roles = signal<Role[]>([]);
  protected readonly choices = signal<Record<string, Choice>>({});
  protected readonly error = signal<string | null>(null);
  protected readonly saving = signal(false);

  protected readonly roleGrants = computed(() => {
    const roleId = this.detail()?.user.roleId;
    return new Set(this.roles().find((role) => role.id === roleId)?.permissions ?? []);
  });

  ngOnInit(): void {
    this.load();
    this.http.get<PermissionInfo[]>('/api/permissions').subscribe({ next: (list) => this.catalog.set(list), error: () => undefined });
    this.http.get<Role[]>('/api/roles').subscribe({ next: (roles) => this.roles.set(roles), error: () => undefined });
  }

  protected load(): void {
    this.error.set(null);
    this.http.get<UserDetail>(`/api/users/${this.id()}`).subscribe({
      next: (detail) => {
        this.detail.set(detail);
        this.choices.set({ ...detail.overrides });
      },
      error: (error) => this.error.set(errorMessage(error)),
    });
  }

  protected setChoice(code: string, choice: Choice): void {
    this.choices.update((current) => ({ ...current, [code]: choice }));
  }

  protected effective(permission: PermissionInfo): boolean {
    if (!permission.available) return false;
    const choice = this.choices()[permission.code] ?? 'ROLE';
    if (choice === 'DENY') return false;
    if (choice === 'ALLOW') return true;
    return this.roleGrants().has(permission.code);
  }

  protected save(): void {
    const overrides = Object.fromEntries(Object.entries(this.choices()).filter(([, choice]) => choice !== 'ROLE'));
    this.saving.set(true);
    this.http.put<UserDetail>(`/api/users/${this.id()}/permissions`, { overrides }).subscribe({
      next: (detail) => {
        this.saving.set(false);
        this.detail.set(detail);
        this.toast.success('Permissões atualizadas.');
        void this.router.navigate(['/usuarios']);
      },
      error: (error) => {
        this.saving.set(false);
        this.toast.error(errorMessage(error));
      },
    });
  }
}
