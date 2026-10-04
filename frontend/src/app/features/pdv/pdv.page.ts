import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  HostListener,
  inject,
  OnInit,
  signal,
  viewChild,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SessionService } from '../../core/auth/session.service';
import { label, PAYMENT_METHOD_LABELS } from '../../core/i18n/labels';
import {
  Category,
  CustomerSummary,
  FinalizeSaleRequest,
  Page,
  PaymentMethod,
  Product,
  Receipt,
  Sale,
} from '../../core/models/api.models';
import { ConfirmService } from '../../core/ui/confirm.service';
import { ToastService } from '../../core/ui/toast.service';
import { errorMessage, toApiError } from '../../core/util/api-error';
import { newIdempotencyKey, params } from '../../core/util/http-params';
import { AlertComponent } from '../../shared/ui/alert/alert.component';
import { ButtonComponent } from '../../shared/ui/button/button.component';
import { IconComponent } from '../../shared/ui/icon/icon.component';
import { ModalComponent } from '../../shared/ui/modal/modal.component';
import { EmptyStateComponent } from '../../shared/ui/states/states.components';
import { ReceiptViewComponent } from '../sales/receipt-view.component';

interface CartItem {
  product: Product;
  quantity: number;
  discount: number;
}

interface PaymentLine {
  method: PaymentMethod;
  amount: number;
}

const round2 = (value: number) => Math.round(value * 100) / 100;

/**
 * Frente de caixa. Preços exibidos são apenas referência: o backend recalcula tudo com os
 * preços do cadastro. Atalhos: F2 buscar produto, F4 cliente, F9 finalizar.
 */
@Component({
  selector: 'nx-pdv-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CurrencyPipe,
    DecimalPipe,
    FormsModule,
    AlertComponent,
    ButtonComponent,
    IconComponent,
    ModalComponent,
    EmptyStateComponent,
    ReceiptViewComponent,
  ],
  templateUrl: './pdv.page.html',
  styleUrl: './pdv.page.scss',
})
export class PdvPage implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly toast = inject(ToastService);
  private readonly confirm = inject(ConfirmService);
  protected readonly session = inject(SessionService);

  private readonly searchInput = viewChild<ElementRef<HTMLInputElement>>('searchInput');

  protected readonly query = signal('');
  protected readonly categoryId = signal<string | null>(null);
  protected readonly categories = signal<Category[]>([]);
  protected readonly products = signal<Product[]>([]);
  protected readonly searching = signal(false);

  protected readonly cart = signal<CartItem[]>([]);
  protected readonly customer = signal<CustomerSummary | null>(null);
  protected readonly saleDiscount = signal(0);
  protected readonly payments = signal<PaymentLine[]>([{ method: 'PIX', amount: 0 }]);
  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);
  private idempotencyKey = newIdempotencyKey();

  protected readonly customerPickerOpen = signal(false);
  protected readonly customerQuery = signal('');
  protected readonly customerResults = signal<CustomerSummary[]>([]);

  protected readonly receipt = signal<Receipt | null>(null);

  protected readonly methods: PaymentMethod[] = ['PIX', 'CASH', 'CREDIT_CARD', 'DEBIT_CARD', 'OTHER'];
  protected readonly canDiscount = computed(() => this.session.hasPermission('SALE_DISCOUNT'));
  protected readonly canPickCustomer = computed(
    () => this.session.hasFeature('CUSTOMERS') && this.session.hasPermission('CUSTOMER_READ'),
  );

  protected readonly subtotal = computed(() =>
    round2(this.cart().reduce((sum, item) => sum + item.product.salePrice * item.quantity, 0)),
  );
  protected readonly itemDiscounts = computed(() => round2(this.cart().reduce((sum, item) => sum + item.discount, 0)));
  protected readonly total = computed(() => Math.max(0, round2(this.subtotal() - this.itemDiscounts() - this.saleDiscount())));
  protected readonly paid = computed(() => round2(this.payments().reduce((sum, p) => sum + (p.amount || 0), 0)));
  protected readonly remaining = computed(() => Math.max(0, round2(this.total() - this.paid())));
  protected readonly change = computed(() => {
    const excess = round2(this.paid() - this.total());
    const cash = this.payments().filter((p) => p.method === 'CASH').reduce((sum, p) => sum + (p.amount || 0), 0);
    return excess > 0 && cash >= excess ? excess : 0;
  });
  protected readonly itemCount = computed(() => this.cart().reduce((sum, item) => sum + item.quantity, 0));

  private searchTimer: ReturnType<typeof setTimeout> | null = null;

  ngOnInit(): void {
    this.search();
    if (this.session.hasFeature('CATEGORIES') && this.session.hasAnyPermission(['CATEGORY_READ', 'PRODUCT_READ'])) {
      this.http.get<Category[]>('/api/categories', { params: params({ active: true }) }).subscribe({
        next: (categories) => this.categories.set(categories),
        error: () => this.categories.set([]),
      });
    }
    setTimeout(() => this.focusSearch());
  }

  @HostListener('document:keydown', ['$event'])
  protected onKeydown(event: KeyboardEvent): void {
    if (this.receipt()) return;
    if (event.key === 'F2') {
      event.preventDefault();
      this.focusSearch();
    } else if (event.key === 'F4') {
      event.preventDefault();
      if (this.canPickCustomer()) this.openCustomerPicker();
    } else if (event.key === 'F9') {
      event.preventDefault();
      void this.finalize();
    }
  }

  protected focusSearch(): void {
    this.searchInput()?.nativeElement.focus();
    this.searchInput()?.nativeElement.select();
  }

  protected onQuery(value: string): void {
    this.query.set(value);
    if (this.searchTimer) clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => this.search(), 220);
  }

  protected selectCategory(id: string | null): void {
    this.categoryId.set(id);
    this.search();
  }

  protected search(): void {
    this.searching.set(true);
    this.http
      .get<Page<Product>>('/api/products', {
        params: params({ q: this.query().trim(), categoryId: this.categoryId(), active: true, size: 24, sort: 'name' }),
      })
      .subscribe({
        next: (page) => {
          this.products.set(page.content);
          this.searching.set(false);
        },
        error: () => this.searching.set(false),
      });
  }

  /** Enter na busca: tenta código de barras/SKU exato (leitor), senão adiciona o único resultado. */
  protected onEnter(): void {
    const code = this.query().trim();
    if (!code) return;
    this.http.get<Product>(`/api/products/by-code/${encodeURIComponent(code)}`).subscribe({
      next: (product) => {
        this.add(product);
        this.query.set('');
        this.search();
      },
      error: () => {
        const list = this.products();
        if (list.length === 1) {
          this.add(list[0]);
          this.query.set('');
          this.search();
        } else {
          this.toast.info('Nenhum produto com esse código. Selecione na lista.');
        }
      },
    });
  }

  protected add(product: Product): void {
    this.error.set(null);
    this.cart.update((items) => {
      const existing = items.find((item) => item.product.id === product.id);
      if (existing) {
        return items.map((item) => (item === existing ? { ...item, quantity: item.quantity + 1 } : item));
      }
      return [...items, { product, quantity: 1, discount: 0 }];
    });
    this.resetKey();
  }

  protected changeQuantity(item: CartItem, delta: number): void {
    this.setQuantity(item, round3(item.quantity + delta));
  }

  protected setQuantity(item: CartItem, value: number): void {
    const discrete = ['UN', 'CX', 'PCT'].includes(item.product.unit);
    const quantity = discrete ? Math.round(value) : round3(value);
    if (!quantity || quantity <= 0) {
      this.remove(item);
      return;
    }
    this.cart.update((items) => items.map((current) => (current === item ? { ...current, quantity } : current)));
    this.resetKey();
  }

  protected setItemDiscount(item: CartItem, value: number): void {
    const max = round2(item.product.salePrice * item.quantity);
    const discount = Math.min(Math.max(0, value || 0), max);
    this.cart.update((items) => items.map((current) => (current === item ? { ...current, discount } : current)));
    this.resetKey();
  }

  protected remove(item: CartItem): void {
    this.cart.update((items) => items.filter((current) => current !== item));
    this.resetKey();
  }

  protected setSaleDiscount(value: number): void {
    this.saleDiscount.set(Math.max(0, round2(value || 0)));
    this.resetKey();
  }

  protected selectMethod(index: number, method: PaymentMethod): void {
    this.payments.update((lines) => lines.map((line, i) => (i === index ? { ...line, method } : line)));
  }

  protected setAmount(index: number, amount: number): void {
    this.payments.update((lines) => lines.map((line, i) => (i === index ? { ...line, amount: round2(amount || 0) } : line)));
  }

  protected addPaymentLine(): void {
    this.payments.update((lines) => [...lines, { method: 'CASH', amount: this.remaining() }]);
  }

  protected removePaymentLine(index: number): void {
    this.payments.update((lines) => lines.filter((_, i) => i !== index));
  }

  protected fillRemaining(index: number): void {
    const others = this.payments().reduce((sum, line, i) => (i === index ? sum : sum + (line.amount || 0)), 0);
    this.setAmount(index, Math.max(0, round2(this.total() - others)));
  }

  protected methodLabel(method: PaymentMethod): string {
    return label(PAYMENT_METHOD_LABELS, method);
  }

  protected async finalize(): Promise<void> {
    if (this.submitting()) return;
    if (!this.cart().length) {
      this.toast.info('Adicione produtos ao carrinho.');
      return;
    }
    // Pagamento único sem valor informado assume o total.
    let lines = this.payments();
    if (lines.length === 1 && !lines[0].amount) {
      lines = [{ ...lines[0], amount: this.total() }];
      this.payments.set(lines);
    }
    if (this.paid() < this.total()) {
      this.error.set('O valor pago é menor que o total da venda.');
      return;
    }
    const confirmed = await this.confirm.confirm({
      title: 'Finalizar venda',
      message: `Confirmar venda de ${this.itemCount().toLocaleString('pt-BR')} item(ns) no total de ${this.total().toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })}?`,
      confirmText: 'Finalizar (F9)',
    });
    if (!confirmed) return;

    const request: FinalizeSaleRequest = {
      items: this.cart().map((item) => ({
        productId: item.product.id,
        quantity: item.quantity,
        discount: item.discount > 0 ? item.discount : null,
      })),
      customerId: this.customer()?.id ?? null,
      discount: this.saleDiscount() > 0 ? this.saleDiscount() : null,
      payments: lines.filter((line) => line.amount > 0),
    };
    this.submitting.set(true);
    this.error.set(null);
    this.http.post<Sale>('/api/sales', request, { headers: { 'Idempotency-Key': this.idempotencyKey } }).subscribe({
      next: (sale) => {
        this.submitting.set(false);
        this.toast.success(`Venda nº ${sale.number} finalizada.`);
        this.clearSale();
        this.loadReceipt(sale.id);
        this.search();
      },
      error: (error) => {
        this.submitting.set(false);
        const apiError = toApiError(error);
        this.error.set(apiError.message);
        if (apiError.code === 'STOCK_INSUFFICIENT') this.search();
      },
    });
  }

  protected clearSale(): void {
    this.cart.set([]);
    this.customer.set(null);
    this.saleDiscount.set(0);
    this.payments.set([{ method: 'PIX', amount: 0 }]);
    this.error.set(null);
    this.idempotencyKey = newIdempotencyKey();
  }

  protected async cancelCurrentSale(): Promise<void> {
    if (!this.cart().length) return;
    const confirmed = await this.confirm.confirm({
      title: 'Limpar venda',
      message: 'Remover todos os itens do carrinho?',
      confirmText: 'Limpar',
      tone: 'danger',
    });
    if (confirmed) this.clearSale();
  }

  private loadReceipt(saleId: string): void {
    this.http.get<Receipt>(`/api/sales/${saleId}/receipt`).subscribe({
      next: (receipt) => this.receipt.set(receipt),
      error: (error) => this.toast.error(errorMessage(error)),
    });
  }

  protected closeReceipt(): void {
    this.receipt.set(null);
    setTimeout(() => this.focusSearch());
  }

  protected print(): void {
    window.print();
  }

  // ---------- Cliente ----------

  protected openCustomerPicker(): void {
    this.customerPickerOpen.set(true);
    this.searchCustomers('');
  }

  protected searchCustomers(value: string): void {
    this.customerQuery.set(value);
    this.http
      .get<Page<CustomerSummary>>('/api/customers', { params: params({ q: value.trim(), active: true, size: 8 }) })
      .subscribe({ next: (page) => this.customerResults.set(page.content), error: () => this.customerResults.set([]) });
  }

  protected pickCustomer(customer: CustomerSummary | null): void {
    this.customer.set(customer);
    this.customerPickerOpen.set(false);
    this.resetKey();
  }

  /** Qualquer alteração no carrinho gera uma nova chave (é uma nova tentativa de venda). */
  private resetKey(): void {
    this.idempotencyKey = newIdempotencyKey();
  }
}

function round3(value: number): number {
  return Math.round(value * 1000) / 1000;
}
