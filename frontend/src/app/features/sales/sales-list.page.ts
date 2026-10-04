import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { SessionService } from '../../core/auth/session.service';
import { REFUND_STATUS, SALE_STATUS } from '../../core/i18n/labels';
import { Page, SaleSummary } from '../../core/models/api.models';
import { errorMessage } from '../../core/util/api-error';
import { params } from '../../core/util/http-params';
import { BadgeComponent } from '../../shared/ui/badge/badge.component';
import { ButtonComponent } from '../../shared/ui/button/button.component';
import { IconComponent } from '../../shared/ui/icon/icon.component';
import { PeriodFilterComponent, periodParams, PeriodValue } from '../../shared/ui/period-filter/period-filter.component';
import {
  EmptyStateComponent,
  ErrorStateComponent,
  PageHeaderComponent,
  PaginationComponent,
  SkeletonComponent,
} from '../../shared/ui/states/states.components';

@Component({
  selector: 'nx-sales-list-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CurrencyPipe,
    DatePipe,
    FormsModule,
    RouterLink,
    BadgeComponent,
    ButtonComponent,
    IconComponent,
    PeriodFilterComponent,
    EmptyStateComponent,
    ErrorStateComponent,
    PageHeaderComponent,
    PaginationComponent,
    SkeletonComponent,
  ],
  template: `
    <div class="nx-page">
      <nx-page-header title="Vendas" subtitle="Histórico de vendas do PDV">
        @if (session.hasPermission('PDV_ACCESS')) {
          <a nxButton routerLink="/pdv"><nx-icon name="cart" [size]="16" /> Abrir PDV</a>
        }
      </nx-page-header>

      <div class="nx-toolbar">
        <nx-period-filter [value]="period()" (valueChange)="period.set($event); reload()" />
        <input class="nx-control" type="search" placeholder="Nº da venda" inputmode="numeric" [ngModel]="number()"
               (ngModelChange)="number.set($event)" (keydown.enter)="reload()" aria-label="Número da venda" />
        <select class="nx-control" [ngModel]="status()" (ngModelChange)="status.set($event); reload()" aria-label="Status">
          <option value="">Todos os status</option>
          <option value="COMPLETED">Concluídas</option>
          <option value="CANCELED">Canceladas</option>
        </select>
      </div>

      <div class="nx-table-wrap">
        @if (error(); as message) {
          <nx-error-state [message]="message" (retry)="load()" />
        } @else if (loading() && !page()) {
          <div style="padding: 1rem"><nx-skeleton [rows]="6" /></div>
        } @else if (page(); as p) {
          @if (p.content.length) {
            <div class="nx-table-scroll">
              <table class="nx-table nx-table--stack">
                <thead>
                  <tr><th>Venda</th><th>Data</th><th>Cliente</th><th>Operador</th><th class="num">Total</th><th>Status</th><th></th></tr>
                </thead>
                <tbody>
                  @for (sale of p.content; track sale.id) {
                    <tr>
                      <td class="nx-stack-main"><a class="nx-table__main" [routerLink]="['/vendas', sale.id]">#{{ sale.number }}</a></td>
                      <td data-label="Data">{{ sale.createdAt | date: 'dd/MM/yyyy HH:mm' }}</td>
                      <td data-label="Cliente">{{ sale.customerName || 'Consumidor final' }}</td>
                      <td data-label="Operador">{{ sale.operatorName }}</td>
                      <td class="num" data-label="Total">{{ sale.total | currency }}</td>
                      <td class="badges">
                        <nx-badge [tone]="statuses[sale.status].tone">{{ statuses[sale.status].label }}</nx-badge>
                        @if (sale.refundStatus && refundStatuses[sale.refundStatus]; as refund) {
                          <nx-badge [tone]="refund.tone">{{ refund.label }}</nx-badge>
                        }
                      </td>
                      <td class="nx-table__actions">
                        <a class="nx-icon-button" [routerLink]="['/vendas', sale.id]" aria-label="Ver venda"><nx-icon name="eye" [size]="16" /></a>
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
            <nx-pagination [page]="p.page" [totalPages]="p.totalPages" [totalElements]="p.totalElements" (pageChange)="goTo($event)" />
          } @else {
            <nx-empty-state icon="receipt" title="Nenhuma venda encontrada" message="Ajuste o período ou os filtros." />
          }
        }
      </div>
    </div>
  `,
  styles: `
    .badges nx-badge + nx-badge { margin-left: var(--nx-space-2); }
  `,
})
export class SalesListPage implements OnInit {
  private readonly http = inject(HttpClient);
  protected readonly session = inject(SessionService);
  protected readonly statuses = SALE_STATUS;
  protected readonly refundStatuses = REFUND_STATUS;

  protected readonly period = signal<PeriodValue>({ preset: 'LAST_7_DAYS' });
  protected readonly number = signal('');
  protected readonly status = signal('');
  protected readonly pageIndex = signal(0);
  protected readonly page = signal<Page<SaleSummary> | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.load();
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
    const number = this.number().replace(/\D/g, '');
    this.http
      .get<Page<SaleSummary>>('/api/sales', {
        params: params({ ...periodParams(this.period()), number, status: this.status(), page: this.pageIndex(), size: 20 }),
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
