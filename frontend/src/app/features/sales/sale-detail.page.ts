import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, input, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { SessionService } from '../../core/auth/session.service';
import { label, PAYMENT_METHOD_LABELS, REFUND_STATUS, SALE_STATUS } from '../../core/i18n/labels';
import { Receipt, Sale } from '../../core/models/api.models';
import { ConfirmService } from '../../core/ui/confirm.service';
import { ToastService } from '../../core/ui/toast.service';
import { errorMessage } from '../../core/util/api-error';
import { AlertComponent } from '../../shared/ui/alert/alert.component';
import { BadgeComponent } from '../../shared/ui/badge/badge.component';
import { ButtonComponent } from '../../shared/ui/button/button.component';
import { IconComponent } from '../../shared/ui/icon/icon.component';
import { ModalComponent } from '../../shared/ui/modal/modal.component';
import { ErrorStateComponent, PageHeaderComponent, SkeletonComponent } from '../../shared/ui/states/states.components';
import { ReceiptViewComponent } from './receipt-view.component';
import { SaleReturnDialogComponent } from './sale-return-dialog.component';

@Component({
  selector: 'nx-sale-detail-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CurrencyPipe,
    DatePipe,
    DecimalPipe,
    RouterLink,
    AlertComponent,
    BadgeComponent,
    ButtonComponent,
    IconComponent,
    ModalComponent,
    ErrorStateComponent,
    PageHeaderComponent,
    SkeletonComponent,
    ReceiptViewComponent,
    SaleReturnDialogComponent,
  ],
  template: `
    <div class="nx-page">
      @if (error(); as message) {
        <nx-error-state [message]="message" (retry)="load()" />
      } @else if (!sale()) {
        <nx-skeleton [rows]="8" />
      } @else {
        @let s = sale()!;
        <nx-page-header [title]="'Venda #' + s.number" [subtitle]="(s.createdAt | date: 'dd/MM/yyyy HH:mm') + ' • ' + s.operatorName">
          @if (session.hasPermission('SALE_READ')) {
            <a nxButton variant="secondary" routerLink="/vendas"><nx-icon name="arrow-left" [size]="16" /> Voltar</a>
          }
          <button nxButton variant="secondary" type="button" (click)="openReceipt()"><nx-icon name="printer" [size]="16" /> Comprovante</button>
          @if (s.status === 'COMPLETED' && s.refundStatus !== 'FULL' && session.hasPermission('SALE_REFUND')) {
            <button nxButton variant="secondary" type="button" (click)="returning.set(true)">
              <nx-icon name="undo" [size]="16" /> Registrar devolução
            </button>
          }
          @if (s.status === 'COMPLETED' && s.refundStatus === 'NONE' && session.hasPermission('SALE_CANCEL')) {
            <button nxButton variant="danger" type="button" [loading]="canceling()" (click)="cancel()">
              <nx-icon name="x-circle" [size]="16" /> Cancelar venda
            </button>
          }
        </nx-page-header>

        @if (s.status === 'CANCELED') {
          <nx-alert tone="danger">
            Venda cancelada em {{ s.canceledAt | date: 'dd/MM/yyyy HH:mm' }} por {{ s.canceledByName }}. Motivo: {{ s.cancelReason }}.
            O estoque dos itens foi restaurado.
          </nx-alert>
        }
        @if (s.refundStatus !== 'NONE') {
          <nx-alert [tone]="s.refundStatus === 'FULL' ? 'info' : 'warning'">
            {{ s.refundStatus === 'FULL' ? 'Todos os itens desta venda foram devolvidos.' : 'Esta venda tem devolução parcial.' }}
            Total estornado: {{ s.refundedTotal | currency }}. Vendas com devolução não podem ser canceladas.
          </nx-alert>
        }

        <div class="nx-grid nx-grid--main-side">
          <section class="nx-table-wrap">
            <table class="nx-table nx-table--stack">
              <thead><tr><th>#</th><th>Produto</th><th class="num">Qtd.</th><th class="num">Preço</th><th class="num">Desconto</th><th class="num">Total</th>
                @if (s.refundStatus !== 'NONE') { <th class="num">Devolvido</th> }
              </tr></thead>
              <tbody>
                @for (item of s.items; track item.lineNumber) {
                  <tr>
                    <td data-label="Item">{{ item.lineNumber }}</td>
                    <td class="nx-stack-main"><span class="nx-table__main">{{ item.description }}</span>
                      @if (item.sku) { <span class="nx-table__sub">SKU {{ item.sku }}</span> }
                    </td>
                    <td class="num" data-label="Qtd.">{{ item.quantity | number: '1.0-3' }}</td>
                    <td class="num" data-label="Preço">{{ item.unitPrice | currency }}</td>
                    <td class="num" data-label="Desconto">{{ item.discount | currency }}</td>
                    <td class="num" data-label="Total">{{ item.total | currency }}</td>
                    @if (s.refundStatus !== 'NONE') {
                      <td class="num" data-label="Devolvido">{{ item.returnedQuantity | number: '1.0-3' }}</td>
                    }
                  </tr>
                }
              </tbody>
            </table>
          </section>
          <section class="nx-panel">
            <div class="nx-panel__header">
              <h2 class="nx-panel__title">Resumo</h2>
              <span class="badges">
                <nx-badge [tone]="statuses[s.status].tone">{{ statuses[s.status].label }}</nx-badge>
                @if (refundStatuses[s.refundStatus]; as refund) { <nx-badge [tone]="refund.tone">{{ refund.label }}</nx-badge> }
              </span>
            </div>
            <dl class="nx-definition">
              <dt>Cliente</dt><dd>{{ s.customerName || 'Consumidor final' }}</dd>
              <dt>Subtotal</dt><dd>{{ s.subtotal | currency }}</dd>
              <dt>Descontos</dt><dd>{{ s.discount | currency }}</dd>
              <dt>Total</dt><dd class="text-primary">{{ s.total | currency }}</dd>
              @for (payment of s.payments; track $index) {
                <dt>{{ methodLabel(payment.method) }}</dt><dd>{{ payment.amount | currency }}</dd>
              }
              @if (s.changeAmount > 0) { <dt>Troco</dt><dd>{{ s.changeAmount | currency }}</dd> }
              @if (s.refundedTotal > 0) {
                <dt>Estornado</dt><dd class="text-danger">− {{ s.refundedTotal | currency }}</dd>
                <dt>Valor líquido</dt><dd>{{ s.total - s.refundedTotal | currency }}</dd>
              }
            </dl>
          </section>
        </div>

        @if (s.returns.length) {
          <section class="nx-panel">
            <div class="nx-panel__header"><h2 class="nx-panel__title">Devoluções</h2></div>
            @for (r of s.returns; track r.id) {
              <div class="return-entry">
                <div class="return-entry__head">
                  <strong>{{ r.createdAt | date: 'dd/MM/yyyy HH:mm' }}</strong>
                  <span class="text-muted text-small">{{ r.userName }} • estorno em {{ methodLabel(r.refundMethod) }}</span>
                  <span class="num text-danger">− {{ r.total | currency }}</span>
                </div>
                <p class="text-small text-muted">Motivo: {{ r.reason }}</p>
                <ul class="text-small">
                  @for (item of r.items; track item.lineNumber) {
                    <li>
                      {{ item.quantity | number: '1.0-3' }} × {{ item.description }} — {{ item.amount | currency }}
                      @if (!item.restocked) { <nx-badge tone="warning">Não voltou ao estoque</nx-badge> }
                    </li>
                  }
                </ul>
              </div>
            }
          </section>
        }
      }
    </div>

    @if (returning() && sale(); as s) {
      <nx-sale-return-dialog [sale]="s" (closed)="returning.set(false)" (returned)="onReturned($event)" />
    }

    @if (receipt(); as r) {
      <nx-modal title="Comprovante" (closed)="receipt.set(null)">
        <nx-receipt-view [receipt]="r" />
        <ng-container modalFooter>
          <button nxButton type="button" (click)="print()"><nx-icon name="printer" [size]="16" /> Imprimir</button>
        </ng-container>
      </nx-modal>
    }
  `,
  styles: `
    .badges { display: inline-flex; gap: var(--nx-space-2); flex-wrap: wrap; }
    .return-entry { padding: var(--nx-space-3) 0; border-top: 1px solid var(--nx-border); }
    .return-entry:first-of-type { border-top: 0; }
    .return-entry__head { display: flex; gap: var(--nx-space-3); align-items: baseline; flex-wrap: wrap; }
    .return-entry__head .num { margin-left: auto; }
    .return-entry p { margin: var(--nx-space-1) 0; }
    .return-entry ul { margin: 0; padding-left: var(--nx-space-5); }
    .return-entry li { display: flex; gap: var(--nx-space-2); align-items: center; flex-wrap: wrap; }
  `,
})
export class SaleDetailPage implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);
  protected readonly session = inject(SessionService);
  protected readonly statuses = SALE_STATUS;
  protected readonly refundStatuses = REFUND_STATUS;
  protected readonly returning = signal(false);

  readonly id = input.required<string>();
  protected readonly sale = signal<Sale | null>(null);
  protected readonly receipt = signal<Receipt | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly canceling = signal(false);

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.error.set(null);
    this.http.get<Sale>(`/api/sales/${this.id()}`).subscribe({
      next: (sale) => this.sale.set(sale),
      error: (error) => this.error.set(errorMessage(error)),
    });
  }

  protected async cancel(): Promise<void> {
    const result = await this.confirm.ask({
      title: `Cancelar venda #${this.sale()?.number}`,
      message: 'A venda será mantida como cancelada e o estoque dos itens será restaurado. Esta ação não pode ser desfeita.',
      confirmText: 'Cancelar venda',
      tone: 'danger',
      requireReason: true,
      reasonLabel: 'Motivo do cancelamento',
    });
    if (!result.confirmed) return;
    this.canceling.set(true);
    this.http.post<Sale>(`/api/sales/${this.id()}/cancel`, { reason: result.reason }).subscribe({
      next: (sale) => {
        this.sale.set(sale);
        this.canceling.set(false);
        this.toast.success('Venda cancelada e estoque restaurado.');
      },
      error: (error) => {
        this.canceling.set(false);
        this.toast.error(errorMessage(error));
      },
    });
  }

  protected onReturned(sale: Sale): void {
    this.sale.set(sale);
    this.returning.set(false);
    this.toast.success('Devolução registrada.');
  }

  protected openReceipt(): void {
    this.http.get<Receipt>(`/api/sales/${this.id()}/receipt`).subscribe({
      next: (receipt) => this.receipt.set(receipt),
      error: (error) => this.toast.error(errorMessage(error)),
    });
  }

  protected print(): void {
    window.print();
  }

  protected methodLabel(method: string): string {
    return label(PAYMENT_METHOD_LABELS, method);
  }
}
