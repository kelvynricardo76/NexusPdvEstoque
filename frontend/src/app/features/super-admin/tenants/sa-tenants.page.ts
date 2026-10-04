import { DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TENANT_STATUS } from '../../../core/i18n/labels';
import { Page } from '../../../core/models/api.models';
import { PlanView, TenantSummary } from '../../../core/models/super-admin.models';
import { errorMessage } from '../../../core/util/api-error';
import { params } from '../../../core/util/http-params';
import { BadgeComponent } from '../../../shared/ui/badge/badge.component';
import { ButtonComponent } from '../../../shared/ui/button/button.component';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import {
  EmptyStateComponent,
  ErrorStateComponent,
  PageHeaderComponent,
  PaginationComponent,
  SkeletonComponent,
} from '../../../shared/ui/states/states.components';

@Component({
  selector: 'nx-sa-tenants-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, FormsModule, RouterLink, BadgeComponent, ButtonComponent, IconComponent, EmptyStateComponent, ErrorStateComponent, PageHeaderComponent, PaginationComponent, SkeletonComponent],
  template: `
    <div class="nx-page">
      <nx-page-header title="Empresas" subtitle="Clientes da plataforma Nexus PDV & Estoque">
        <a nxButton routerLink="/super-admin/empresas/nova"><nx-icon name="plus" [size]="16" /> Nova empresa</a>
      </nx-page-header>
      <div class="nx-toolbar">
        <div class="nx-search">
          <nx-icon name="search" [size]="16" />
          <input class="nx-control" type="search" placeholder="Nome ou documento" [ngModel]="query()" (ngModelChange)="query.set($event)" (keydown.enter)="reload()" />
        </div>
        <select class="nx-control" [ngModel]="status()" (ngModelChange)="status.set($event); reload()" aria-label="Status">
          <option value="">Todos os status</option>
          @for (entry of statusEntries; track entry[0]) { <option [value]="entry[0]">{{ entry[1].label }}</option> }
        </select>
        <select class="nx-control" [ngModel]="planCode()" (ngModelChange)="planCode.set($event); reload()" aria-label="Plano">
          <option value="">Todos os planos</option>
          @for (plan of plans(); track plan.code) { <option [value]="plan.code">{{ plan.name }}</option> }
        </select>
      </div>
      <div class="nx-table-wrap">
        @if (error(); as message) {
          <nx-error-state [message]="message" (retry)="load()" />
        } @else if (loading() && !page()) {
          <div style="padding: 1rem"><nx-skeleton [rows]="8" /></div>
        } @else if (page(); as p) {
          @if (p.content.length) {
            <table class="nx-table nx-table--stack">
              <thead><tr><th>Empresa</th><th>Plano</th><th>Status</th><th class="num">Usuários</th><th>Criada em</th><th>Assinatura</th><th></th></tr></thead>
              <tbody>
                @for (tenant of p.content; track tenant.id) {
                  <tr>
                    <td class="nx-stack-main">
                      <a class="nx-table__main" [routerLink]="['/super-admin/empresas', tenant.id]">{{ tenant.tradeName || tenant.name }}</a>
                      <span class="nx-table__sub">{{ tenant.document || tenant.name }}</span>
                    </td>
                    <td data-label="Plano">{{ tenant.planName || '—' }}</td>
                    <td><nx-badge [tone]="statuses[tenant.status].tone">{{ statuses[tenant.status].label }}</nx-badge></td>
                    <td class="num" data-label="Usuários">{{ tenant.activeUsers }}</td>
                    <td data-label="Criada em">{{ tenant.createdAt | date: 'dd/MM/yyyy' }}</td>
                    <td data-label="Assinatura">{{ tenant.subscriptionStatus ? statuses[tenant.subscriptionStatus]?.label : '—' }}</td>
                    <td class="nx-table__actions">
                      <a class="nx-icon-button" [routerLink]="['/super-admin/empresas', tenant.id]" aria-label="Detalhes"><nx-icon name="chevron-right" [size]="16" /></a>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
            <nx-pagination [page]="p.page" [totalPages]="p.totalPages" [totalElements]="p.totalElements" (pageChange)="goTo($event)" />
          } @else {
            <nx-empty-state icon="building" title="Nenhuma empresa encontrada" />
          }
        }
      </div>
    </div>
  `,
})
export class SaTenantsPage implements OnInit {
  private readonly http = inject(HttpClient);
  protected readonly statuses = TENANT_STATUS;
  protected readonly statusEntries = Object.entries(TENANT_STATUS);

  protected readonly query = signal('');
  protected readonly status = signal('');
  protected readonly planCode = signal('');
  protected readonly plans = signal<PlanView[]>([]);
  protected readonly pageIndex = signal(0);
  protected readonly page = signal<Page<TenantSummary> | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.load();
    this.http.get<PlanView[]>('/api/super-admin/plans').subscribe({ next: (plans) => this.plans.set(plans), error: () => undefined });
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
      .get<Page<TenantSummary>>('/api/super-admin/tenants', {
        params: params({ q: this.query().trim(), status: this.status(), planCode: this.planCode(), page: this.pageIndex(), size: 20 }),
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
}
