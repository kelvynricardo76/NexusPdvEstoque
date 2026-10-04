import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { INVOICE_STATUS } from '../../../core/i18n/labels';
import { Page } from '../../../core/models/api.models';
import { InvoiceView, TenantSummary } from '../../../core/models/super-admin.models';
import { ConfirmService } from '../../../core/ui/confirm.service';
import { ToastService } from '../../../core/ui/toast.service';
import { errorMessage } from '../../../core/util/api-error';
import { params } from '../../../core/util/http-params';
import { AlertComponent } from '../../../shared/ui/alert/alert.component';
import { BadgeComponent } from '../../../shared/ui/badge/badge.component';
import { ButtonComponent } from '../../../shared/ui/button/button.component';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { ModalComponent } from '../../../shared/ui/modal/modal.component';
import { EmptyStateComponent, ErrorStateComponent, PageHeaderComponent, PaginationComponent } from '../../../shared/ui/states/states.components';

/** Cobranças da assinatura SaaS (tenant → Nexus). Não é o financeiro dos clientes. */
@Component({
  selector: 'nx-sa-billing-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CurrencyPipe, DatePipe, FormsModule, RouterLink, AlertComponent, BadgeComponent, ButtonComponent, IconComponent, ModalComponent, EmptyStateComponent, ErrorStateComponent, PageHeaderComponent, PaginationComponent],
  template: `
    <div class="nx-page">
      <nx-page-header title="Cobranças" subtitle="Faturas das assinaturas (cobrança manual — pronta para gateway)">
        <button nxButton type="button" (click)="openIssue()"><nx-icon name="plus" [size]="16" /> Emitir fatura</button>
      </nx-page-header>
      <div class="nx-toolbar">
        <select class="nx-control" [ngModel]="status()" (ngModelChange)="status.set($event); reload()" aria-label="Status">
          <option value="">Todos os status</option>
          @for (entry of statusEntries; track entry[0]) { <option [value]="entry[0]">{{ entry[1].label }}</option> }
        </select>
      </div>
      <div class="nx-table-wrap">
        @if (error(); as message) {
          <nx-error-state [message]="message" (retry)="load()" />
        } @else if (page(); as p) {
          @if (p.content.length) {
            <table class="nx-table nx-table--stack">
              <thead><tr><th>Empresa</th><th>Descrição</th><th>Vencimento</th><th class="num">Valor</th><th>Status</th><th></th></tr></thead>
              <tbody>
                @for (invoice of p.content; track invoice.id) {
                  <tr>
                    <td class="nx-stack-main"><a class="nx-table__main" [routerLink]="['/super-admin/empresas', invoice.tenantId]">{{ invoice.tenantName }}</a></td>
                    <td data-label="Descrição" class="text-muted">{{ invoice.description }}</td>
                    <td data-label="Vencimento">{{ invoice.dueDate | date: 'dd/MM/yyyy' }}</td>
                    <td class="num" data-label="Valor">{{ invoice.amount | currency }}</td>
                    <td><nx-badge [tone]="statuses[invoice.status].tone">{{ statuses[invoice.status].label }}</nx-badge></td>
                    <td class="nx-table__actions">
                      @if (invoice.status === 'PENDING' || invoice.status === 'OVERDUE') {
                        <button nxButton size="sm" variant="secondary" type="button" (click)="pay(invoice)">Baixar</button>
                        <button type="button" class="nx-icon-button is-danger" (click)="cancel(invoice)" aria-label="Cancelar"><nx-icon name="x-circle" [size]="16" /></button>
                      }
                    </td>
                  </tr>
                }
              </tbody>
            </table>
            <nx-pagination [page]="p.page" [totalPages]="p.totalPages" [totalElements]="p.totalElements" (pageChange)="goTo($event)" />
          } @else {
            <nx-empty-state icon="credit-card" title="Nenhuma fatura" />
          }
        }
      </div>
    </div>

    @if (issuing()) {
      <nx-modal title="Emitir fatura" size="sm" (closed)="issuing.set(false)">
        <label class="nx-label" for="b-tenant">Empresa *</label>
        <select id="b-tenant" class="nx-control" [ngModel]="tenantId()" (ngModelChange)="tenantId.set($event)">
          <option value="">Selecione</option>
          @for (tenant of tenants(); track tenant.id) { <option [value]="tenant.id">{{ tenant.tradeName || tenant.name }} ({{ tenant.planName }})</option> }
        </select>
        <label class="nx-label" for="b-amount" style="margin-top: 1rem">Valor (vazio = preço do plano)</label>
        <input id="b-amount" class="nx-control" type="number" min="0" step="0.01" [ngModel]="amount()" (ngModelChange)="amount.set($event)" />
        <label class="nx-label" for="b-due" style="margin-top: 1rem">Vencimento (vazio = fim do período atual)</label>
        <input id="b-due" class="nx-control" type="date" [ngModel]="dueDate()" (ngModelChange)="dueDate.set($event)" />
        @if (issueError(); as message) { <nx-alert tone="danger" style="margin-top: 1rem">{{ message }}</nx-alert> }
        <ng-container modalFooter>
          <button nxButton variant="secondary" type="button" (click)="issuing.set(false)">Cancelar</button>
          <button nxButton type="button" (click)="issue()">Emitir</button>
        </ng-container>
      </nx-modal>
    }
  `,
})
export class SaBillingPage implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly toast = inject(ToastService);
  private readonly confirm = inject(ConfirmService);
  protected readonly statuses = INVOICE_STATUS;
  protected readonly statusEntries = Object.entries(INVOICE_STATUS);

  protected readonly status = signal('');
  protected readonly pageIndex = signal(0);
  protected readonly page = signal<Page<InvoiceView> | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly issuing = signal(false);
  protected readonly tenants = signal<TenantSummary[]>([]);
  protected readonly tenantId = signal('');
  protected readonly amount = signal<number | null>(null);
  protected readonly dueDate = signal('');
  protected readonly issueError = signal<string | null>(null);

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
    this.error.set(null);
    this.http
      .get<Page<InvoiceView>>('/api/super-admin/billing/invoices', { params: params({ status: this.status(), page: this.pageIndex(), size: 20 }) })
      .subscribe({ next: (page) => this.page.set(page), error: (error) => this.error.set(errorMessage(error)) });
  }

  protected openIssue(): void {
    this.issueError.set(null);
    this.tenantId.set('');
    this.amount.set(null);
    this.dueDate.set('');
    this.issuing.set(true);
    this.http.get<Page<TenantSummary>>('/api/super-admin/tenants?size=100').subscribe({
      next: (page) => this.tenants.set(page.content),
      error: () => undefined,
    });
  }

  protected issue(): void {
    if (!this.tenantId()) {
      this.issueError.set('Selecione a empresa.');
      return;
    }
    this.http
      .post('/api/super-admin/billing/invoices', {
        tenantId: this.tenantId(),
        amount: this.amount() || null,
        dueDate: this.dueDate() || null,
      })
      .subscribe({
        next: () => {
          this.issuing.set(false);
          this.toast.success('Fatura emitida.');
          this.load();
        },
        error: (error) => this.issueError.set(errorMessage(error)),
      });
  }

  protected async pay(invoice: InvoiceView): Promise<void> {
    if (!(await this.confirm.confirm({
      title: 'Baixar fatura',
      message: `Confirmar o pagamento da fatura de ${invoice.tenantName}? A assinatura será reativada e o período renovado.`,
      confirmText: 'Confirmar pagamento',
    }))) return;
    this.http.post(`/api/super-admin/billing/invoices/${invoice.id}/pay`, {}).subscribe({
      next: () => {
        this.toast.success('Pagamento registrado.');
        this.load();
      },
      error: (error) => this.toast.error(errorMessage(error)),
    });
  }

  protected async cancel(invoice: InvoiceView): Promise<void> {
    if (!(await this.confirm.confirm({ title: 'Cancelar fatura', message: 'Cancelar esta fatura?', confirmText: 'Cancelar fatura', tone: 'danger' }))) return;
    this.http.post(`/api/super-admin/billing/invoices/${invoice.id}/cancel`, {}).subscribe({
      next: () => this.load(),
      error: (error) => this.toast.error(errorMessage(error)),
    });
  }
}
