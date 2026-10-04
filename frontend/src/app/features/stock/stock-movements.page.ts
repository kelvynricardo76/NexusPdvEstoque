import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { label, MOVEMENT_TYPE_LABELS } from '../../core/i18n/labels';
import { Page, StockMovement } from '../../core/models/api.models';
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

const INBOUND = new Set(['INITIAL', 'ENTRY', 'RETURN', 'POSITIVE_ADJUSTMENT', 'SALE_CANCELLATION']);

@Component({
  selector: 'nx-stock-movements-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, DecimalPipe, FormsModule, RouterLink, BadgeComponent, ButtonComponent, IconComponent, PeriodFilterComponent, EmptyStateComponent, ErrorStateComponent, PageHeaderComponent, PaginationComponent, SkeletonComponent],
  template: `
    <div class="nx-page">
      <nx-page-header title="Movimentações de estoque" subtitle="Toda alteração de estoque fica registrada">
        <a nxButton variant="secondary" routerLink="/estoque"><nx-icon name="arrow-left" [size]="16" /> Estoque</a>
      </nx-page-header>
      <div class="nx-toolbar">
        <nx-period-filter [value]="period()" (valueChange)="period.set($event); reload()" />
        <select class="nx-control" [ngModel]="type()" (ngModelChange)="type.set($event); reload()" aria-label="Tipo">
          <option value="">Todos os tipos</option>
          @for (entry of types; track entry[0]) { <option [value]="entry[0]">{{ entry[1] }}</option> }
        </select>
      </div>
      <div class="nx-table-wrap">
        @if (error(); as message) {
          <nx-error-state [message]="message" (retry)="load()" />
        } @else if (loading() && !page()) {
          <div style="padding: 1rem"><nx-skeleton [rows]="8" /></div>
        } @else if (page(); as p) {
          @if (p.content.length) {
            <div class="nx-table-scroll">
              <table class="nx-table nx-table--stack">
                <thead><tr><th>Data</th><th>Produto</th><th>Tipo</th><th class="num">Qtd.</th><th class="num">Anterior → Novo</th><th>Motivo</th><th>Usuário</th></tr></thead>
                <tbody>
                  @for (m of p.content; track m.id) {
                    <tr>
                      <td data-label="Data">{{ m.createdAt | date: 'dd/MM/yyyy HH:mm' }}</td>
                      <td class="nx-stack-main nx-table__main">{{ m.productName }}</td>
                      <td><nx-badge [tone]="inbound.has(m.type) ? 'success' : 'warning'">{{ typeLabel(m.type) }}</nx-badge></td>
                      <td class="num" data-label="Qtd." [class.text-primary]="inbound.has(m.type)">{{ inbound.has(m.type) ? '+' : '−' }}{{ m.quantity | number: '1.0-3' }}</td>
                      <td class="num" data-label="Saldo">{{ m.previousStock | number: '1.0-3' }} → {{ m.newStock | number: '1.0-3' }}</td>
                      <td data-label="Motivo" class="text-muted">{{ m.reason || '—' }}</td>
                      <td data-label="Usuário">{{ m.userName || 'Sistema' }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
            <nx-pagination [page]="p.page" [totalPages]="p.totalPages" [totalElements]="p.totalElements" (pageChange)="goTo($event)" />
          } @else {
            <nx-empty-state icon="repeat" title="Nenhuma movimentação no período" />
          }
        }
      </div>
    </div>
  `,
})
export class StockMovementsPage implements OnInit {
  private readonly http = inject(HttpClient);
  protected readonly inbound = INBOUND;
  protected readonly types = Object.entries(MOVEMENT_TYPE_LABELS);

  protected readonly period = signal<PeriodValue>({ preset: 'LAST_7_DAYS' });
  protected readonly type = signal('');
  protected readonly pageIndex = signal(0);
  protected readonly page = signal<Page<StockMovement> | null>(null);
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

  protected typeLabel(type: string): string {
    return label(MOVEMENT_TYPE_LABELS, type);
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.http
      .get<Page<StockMovement>>('/api/stock/movements', {
        params: params({ ...periodParams(this.period()), type: this.type(), page: this.pageIndex(), size: 30 }),
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
