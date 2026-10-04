import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { label, PAYMENT_METHOD_LABELS } from '../../core/i18n/labels';
import { Receipt } from '../../core/models/api.models';

/** Comprovante não fiscal no formato de bobina (80 mm), pronto para impressão. */
@Component({
  selector: 'nx-receipt-view',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CurrencyPipe, DatePipe, DecimalPipe],
  template: `
    @let sale = receipt().sale;
    <div class="receipt nx-print-area">
      @if (receipt().logoDataUrl) {
        <img class="receipt__logo" [src]="receipt().logoDataUrl" alt="" />
      }
      <strong class="receipt__company">{{ receipt().tradeName || receipt().companyName }}</strong>
      @if (receipt().document) {
        <span>CNPJ/CPF {{ receipt().document }}</span>
      }
      @if (receipt().address) {
        <span>{{ receipt().address }}</span>
      }
      <hr />
      <span>Venda nº {{ sale.number }} • {{ sale.createdAt | date: 'dd/MM/yyyy HH:mm' }}</span>
      <span>Operador: {{ sale.operatorName }}</span>
      @if (sale.customerName) {
        <span>Cliente: {{ sale.customerName }}</span>
      }
      <hr />
      @for (item of sale.items; track item.lineNumber) {
        <div class="receipt__item">
          <span class="receipt__desc">{{ item.lineNumber }}. {{ item.description }}</span>
          <span class="receipt__row">
            <span>{{ item.quantity | number: '1.0-3' }} x {{ item.unitPrice | currency }}</span>
            <span>{{ item.total | currency }}</span>
          </span>
          @if (item.discount > 0) {
            <span class="receipt__row receipt__muted"><span>Desconto</span><span>-{{ item.discount | currency }}</span></span>
          }
        </div>
      }
      <hr />
      <span class="receipt__row"><span>Subtotal</span><span>{{ sale.subtotal | currency }}</span></span>
      @if (sale.discount > 0) {
        <span class="receipt__row"><span>Descontos</span><span>-{{ sale.discount | currency }}</span></span>
      }
      <strong class="receipt__row receipt__total"><span>TOTAL</span><span>{{ sale.total | currency }}</span></strong>
      @for (payment of sale.payments; track $index) {
        <span class="receipt__row"><span>{{ methodLabel(payment.method) }}</span><span>{{ payment.amount | currency }}</span></span>
      }
      @if (sale.changeAmount > 0) {
        <span class="receipt__row"><span>Troco</span><span>{{ sale.changeAmount | currency }}</span></span>
      }
      @if (sale.status === 'CANCELED') {
        <strong class="receipt__canceled">VENDA CANCELADA</strong>
      }
      <hr />
      <span class="receipt__footer">Documento sem valor fiscal • Nexus PDV &amp; Estoque</span>
    </div>
  `,
  styles: `
    .receipt {
      display: flex;
      flex-direction: column;
      gap: 2px;
      width: 100%;
      max-width: 320px;
      margin: 0 auto;
      padding: 16px;
      border-radius: 6px;
      background: #fff;
      color: #111;
      font-family: ui-monospace, 'Courier New', monospace;
      font-size: 12px;
      line-height: 1.45;
    }
    .receipt__logo { max-width: 120px; max-height: 60px; margin: 0 auto 6px; object-fit: contain; }
    .receipt__company { font-size: 14px; text-align: center; }
    hr { width: 100%; margin: 6px 0; border: 0; border-top: 1px dashed #999; }
    .receipt__item { display: flex; flex-direction: column; }
    .receipt__row { display: flex; justify-content: space-between; gap: 8px; }
    .receipt__muted { color: #555; }
    .receipt__total { margin: 4px 0; font-size: 14px; }
    .receipt__canceled { margin-top: 6px; text-align: center; letter-spacing: 0.1em; }
    .receipt__footer { color: #555; font-size: 10px; text-align: center; }
  `,
})
export class ReceiptViewComponent {
  readonly receipt = input.required<Receipt>();

  protected methodLabel(method: string): string {
    return label(PAYMENT_METHOD_LABELS, method);
  }
}
