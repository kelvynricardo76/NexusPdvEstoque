import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TENANT_STATUS } from '../../../core/i18n/labels';
import { Page } from '../../../core/models/api.models';
import { PlanView, SubscriptionView } from '../../../core/models/super-admin.models';
import { errorMessage } from '../../../core/util/api-error';
import { params } from '../../../core/util/http-params';
import { BadgeComponent } from '../../../shared/ui/badge/badge.component';
import { EmptyStateComponent, ErrorStateComponent, PageHeaderComponent, PaginationComponent } from '../../../shared/ui/states/states.components';

@Component({
  selector: 'nx-sa-subscriptions-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CurrencyPipe, DatePipe, FormsModule, RouterLink, BadgeComponent, EmptyStateComponent, ErrorStateComponent, PageHeaderComponent, PaginationComponent],
  template: `
    <div class="nx-page">
      <nx-page-header title="Assinaturas" subtitle="Relação comercial entre empresas e planos" />
      <div class="nx-toolbar">
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
        } @else if (page(); as p) {
          @if (p.content.length) {
            <table class="nx-table nx-table--stack">
              <thead><tr><th>Empresa</th><th>Plano</th><th>Ciclo</th><th>Status</th><th>Vigência</th><th class="num">MRR</th></tr></thead>
              <tbody>
                @for (s of p.content; track s.id) {
                  <tr>
                    <td class="nx-stack-main"><a class="nx-table__main" [routerLink]="['/super-admin/empresas', s.tenantId]">{{ s.tenantName }}</a></td>
                    <td data-label="Plano">{{ s.planName }}</td>
                    <td data-label="Ciclo">{{ s.billingCycle === 'ANNUAL' ? 'Anual' : 'Mensal' }}</td>
                    <td><nx-badge [tone]="statuses[s.status].tone">{{ statuses[s.status].label }}</nx-badge></td>
                    <td data-label="Vigência">
                      @if (s.status === 'TRIAL') { até {{ s.trialEndDate | date: 'dd/MM/yyyy' }} }
                      @else if (s.currentPeriodEnd) { até {{ s.currentPeriodEnd | date: 'dd/MM/yyyy' }} } @else { — }
                    </td>
                    <td class="num" data-label="MRR">{{ s.monthlyRecurringRevenue | currency }}</td>
                  </tr>
                }
              </tbody>
            </table>
            <nx-pagination [page]="p.page" [totalPages]="p.totalPages" [totalElements]="p.totalElements" (pageChange)="goTo($event)" />
          } @else {
            <nx-empty-state icon="repeat" title="Nenhuma assinatura encontrada" />
          }
        }
      </div>
    </div>
  `,
})
export class SaSubscriptionsPage implements OnInit {
  private readonly http = inject(HttpClient);
  protected readonly statuses = TENANT_STATUS;
  protected readonly statusEntries = Object.entries(TENANT_STATUS);
  protected readonly status = signal('');
  protected readonly planCode = signal('');
  protected readonly plans = signal<PlanView[]>([]);
  protected readonly pageIndex = signal(0);
  protected readonly page = signal<Page<SubscriptionView> | null>(null);
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
    this.error.set(null);
    this.http
      .get<Page<SubscriptionView>>('/api/super-admin/subscriptions', {
        params: params({ status: this.status(), planCode: this.planCode(), page: this.pageIndex(), size: 20 }),
      })
      .subscribe({ next: (page) => this.page.set(page), error: (error) => this.error.set(errorMessage(error)) });
  }
}
