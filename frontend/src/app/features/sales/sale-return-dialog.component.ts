import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { PAYMENT_METHOD_LABELS } from '../../core/i18n/labels';
import { PaymentMethod, Sale, SaleReturnRequest } from '../../core/models/api.models';
import { errorMessage } from '../../core/util/api-error';
import { AlertComponent } from '../../shared/ui/alert/alert.component';
import { ButtonComponent } from '../../shared/ui/button/button.component';
import { ModalComponent } from '../../shared/ui/modal/modal.component';

interface ReturnLine {
  lineNumber: number;
  description: string;
  returnable: number;
  quantity: number;
  restock: boolean;
}

/**
 * Devolução total ou parcial. O valor exibido é uma estimativa; o servidor calcula o estorno
 * (proporcional ao valor pago, com o desconto da venda rateado entre os itens).
 */
@Component({
  selector: 'nx-sale-return-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CurrencyPipe, DecimalPipe, FormsModule, AlertComponent, ButtonComponent, ModalComponent],
  template: `
    <nx-modal [title]="'Devolução da venda #' + sale().number" subtitle="Informe as quantidades devolvidas" size="lg" (closed)="closed.emit()">
      <div class="nx-table-wrap">
        <table class="nx-table nx-table--stack">
          <thead>
            <tr><th>Produto</th><th class="num">Disponível</th><th class="num">Devolver</th><th>Volta ao estoque</th></tr>
          </thead>
          <tbody>
            @for (line of lines(); track line.lineNumber; let i = $index) {
              <tr>
                <td class="nx-stack-main"><span class="nx-table__main">{{ line.description }}</span></td>
                <td class="num" data-label="Disponível">{{ line.returnable | number: '1.0-3' }}</td>
                <td class="num" data-label="Devolver">
                  <input
                    class="nx-control qty"
                    type="number"
                    min="0"
                    [max]="line.returnable"
                    step="any"
                    [attr.aria-label]="'Quantidade a devolver de ' + line.description"
                    [ngModel]="line.quantity"
                    (ngModelChange)="setQuantity(i, $event)"
                  />
                </td>
                <td data-label="Estoque">
                  <label class="nx-check">
                    <input type="checkbox" [ngModel]="line.restock" (ngModelChange)="setRestock(i, $event)" />
                    {{ line.restock ? 'Sim' : 'Não (avariado)' }}
                  </label>
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>

      <div class="nx-form-grid">
        <div>
          <label class="nx-label" for="return-method">Forma de estorno</label>
          <select id="return-method" class="nx-control" [ngModel]="method()" (ngModelChange)="method.set($event)">
            @for (option of methods; track option) {
              <option [value]="option">{{ methodLabels[option] }}</option>
            }
          </select>
        </div>
        <div>
          <span class="nx-label">Valor estimado</span>
          <p class="estimate text-primary">{{ estimate() | currency }}</p>
        </div>
        <div class="nx-form-grid__full">
          <label class="nx-label" for="return-reason">Motivo</label>
          <textarea id="return-reason" class="nx-control" rows="2" maxlength="300" [ngModel]="reason()" (ngModelChange)="reason.set($event)"
                    placeholder="Ex.: produto com defeito, troca, desistência"></textarea>
        </div>
      </div>

      @if (error(); as message) { <nx-alert tone="danger">{{ message }}</nx-alert> }

      <ng-container modalFooter>
        <button nxButton variant="secondary" type="button" (click)="closed.emit()">Voltar</button>
        <button nxButton type="button" [disabled]="!valid()" [loading]="saving()" (click)="submit()">Registrar devolução</button>
      </ng-container>
    </nx-modal>
  `,
  styles: `
    .qty { width: 7rem; text-align: right; margin-left: auto; }
    .estimate { font-size: 1.25rem; font-weight: 600; margin: var(--nx-space-2) 0 0; }
    .nx-form-grid { margin-top: var(--nx-space-4); }
  `,
})
export class SaleReturnDialogComponent {
  private readonly http = inject(HttpClient);

  readonly sale = input.required<Sale>();
  readonly closed = output<void>();
  readonly returned = output<Sale>();

  protected readonly methods: PaymentMethod[] = ['CASH', 'PIX', 'CREDIT_CARD', 'DEBIT_CARD', 'OTHER'];
  protected readonly methodLabels = PAYMENT_METHOD_LABELS;
  protected readonly method = signal<PaymentMethod>('CASH');
  protected readonly reason = signal('');
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  /** Idempotência: a mesma chave em reenvios do mesmo formulário (ex.: duplo clique, rede instável). */
  private readonly idempotencyKey = `ret-${crypto.randomUUID()}`;

  protected readonly lines = signal<ReturnLine[]>([]);

  ngOnInit(): void {
    this.lines.set(
      this.sale()
        .items.map((item) => ({
          lineNumber: item.lineNumber,
          description: item.description,
          returnable: item.quantity - (item.returnedQuantity ?? 0),
          quantity: 0,
          restock: true,
        }))
        .filter((line) => line.returnable > 0),
    );
  }

  protected readonly estimate = computed(() => {
    const sale = this.sale();
    const itemsTotal = sale.items.reduce((sum, item) => sum + item.total, 0);
    if (itemsTotal === 0) return 0;
    return this.lines().reduce((sum, line) => {
      const item = sale.items.find((candidate) => candidate.lineNumber === line.lineNumber)!;
      const net = (item.total * sale.total) / itemsTotal;
      return sum + (net * line.quantity) / item.quantity;
    }, 0);
  });

  protected readonly valid = computed(() => {
    const lines = this.lines();
    return (
      this.reason().trim().length >= 3 &&
      lines.some((line) => line.quantity > 0) &&
      lines.every((line) => line.quantity >= 0 && line.quantity <= line.returnable)
    );
  });

  protected setQuantity(index: number, value: number | null): void {
    this.lines.update((lines) => lines.map((line, i) => (i === index ? { ...line, quantity: Number(value) || 0 } : line)));
  }

  protected setRestock(index: number, value: boolean): void {
    this.lines.update((lines) => lines.map((line, i) => (i === index ? { ...line, restock: value } : line)));
  }

  protected submit(): void {
    if (!this.valid() || this.saving()) return;
    const body: SaleReturnRequest = {
      reason: this.reason().trim(),
      refundMethod: this.method(),
      items: this.lines()
        .filter((line) => line.quantity > 0)
        .map((line) => ({ lineNumber: line.lineNumber, quantity: line.quantity, restock: line.restock })),
    };
    this.saving.set(true);
    this.error.set(null);
    this.http
      .post<Sale>(`/api/sales/${this.sale().id}/returns`, body, { headers: { 'Idempotency-Key': this.idempotencyKey } })
      .subscribe({
        next: (sale) => {
          this.saving.set(false);
          this.returned.emit(sale);
        },
        error: (error) => {
          this.saving.set(false);
          this.error.set(errorMessage(error));
        },
      });
  }
}
