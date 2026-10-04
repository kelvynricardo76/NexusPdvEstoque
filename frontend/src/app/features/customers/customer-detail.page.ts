import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, input, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { SessionService } from '../../core/auth/session.service';
import { SALE_STATUS } from '../../core/i18n/labels';
import { CustomerDetail } from '../../core/models/api.models';
import { ConfirmService } from '../../core/ui/confirm.service';
import { ToastService } from '../../core/ui/toast.service';
import { errorMessage } from '../../core/util/api-error';
import { BadgeComponent } from '../../shared/ui/badge/badge.component';
import { ButtonComponent } from '../../shared/ui/button/button.component';
import { IconComponent } from '../../shared/ui/icon/icon.component';
import { ErrorStateComponent, PageHeaderComponent, SkeletonComponent } from '../../shared/ui/states/states.components';
import { StatCardComponent } from '../../shared/ui/stat-card/stat-card.component';
import { CustomerFormComponent } from './customer-form.component';

@Component({
  selector: 'nx-customer-detail-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CurrencyPipe, DatePipe, RouterLink, BadgeComponent, ButtonComponent, IconComponent, ErrorStateComponent, PageHeaderComponent, SkeletonComponent, StatCardComponent, CustomerFormComponent],
  template: `
    <div class="nx-page">
      @if (error(); as message) {
        <nx-error-state [message]="message" (retry)="load()" />
      } @else if (!customer()) {
        <nx-skeleton [rows]="8" />
      } @else {
        @let c = customer()!;
        <nx-page-header [title]="c.name" [subtitle]="'Cliente desde ' + (c.createdAt | date: 'dd/MM/yyyy')">
          <a nxButton variant="secondary" routerLink="/clientes"><nx-icon name="arrow-left" [size]="16" /> Voltar</a>
          @if (session.hasPermission('CUSTOMER_UPDATE')) {
            <button nxButton variant="secondary" type="button" (click)="editing.set(true)"><nx-icon name="edit" [size]="16" /> Editar</button>
          }
          @if (session.hasPermission('CUSTOMER_DISABLE')) {
            <button nxButton [variant]="c.active ? 'danger' : 'primary'" type="button" (click)="toggle()">{{ c.active ? 'Desativar' : 'Ativar' }}</button>
          }
        </nx-page-header>

        <div class="nx-grid nx-grid--3">
          <nx-stat-card icon="receipt" label="Compras" [value]="c.purchases.toString()" />
          <nx-stat-card icon="dollar" label="Total comprado" [value]="(c.totalPurchased | currency) ?? ''" />
          <nx-stat-card icon="clock" label="Última compra" [value]="c.lastPurchaseAt ? ((c.lastPurchaseAt | date: 'dd/MM/yyyy') ?? '') : '—'" />
        </div>

        <div class="nx-grid nx-grid--main-side">
          <section class="nx-table-wrap">
            <div class="nx-panel__header" style="padding: 1rem 1rem 0"><h2 class="nx-panel__title">Compras recentes</h2></div>
            <table class="nx-table nx-table--stack">
              <thead><tr><th>Venda</th><th>Data</th><th class="num">Total</th><th>Status</th></tr></thead>
              <tbody>
                @for (purchase of c.recentPurchases; track purchase.saleId) {
                  <tr>
                    <td class="nx-stack-main">
                      @if (session.hasPermission('SALE_READ')) {
                        <a class="nx-table__main" [routerLink]="['/vendas', purchase.saleId]">#{{ purchase.number }}</a>
                      } @else { #{{ purchase.number }} }
                    </td>
                    <td data-label="Data">{{ purchase.createdAt | date: 'dd/MM/yyyy HH:mm' }}</td>
                    <td class="num" data-label="Total">{{ purchase.total | currency }}</td>
                    <td><nx-badge [tone]="statuses[purchase.status].tone">{{ statuses[purchase.status].label }}</nx-badge></td>
                  </tr>
                } @empty {
                  <tr><td colspan="4" class="text-muted">Nenhuma compra registrada.</td></tr>
                }
              </tbody>
            </table>
          </section>
          <section class="nx-panel">
            <div class="nx-panel__header">
              <h2 class="nx-panel__title">Dados</h2>
              <nx-badge [tone]="c.active ? 'success' : 'neutral'">{{ c.active ? 'Ativo' : 'Inativo' }}</nx-badge>
            </div>
            <dl class="nx-definition">
              <dt>Documento</dt><dd>{{ c.document || '—' }}</dd>
              <dt>Telefone</dt><dd>{{ c.phone || '—' }}</dd>
              <dt>E-mail</dt><dd>{{ c.email || '—' }}</dd>
              <dt>Endereço</dt><dd>{{ c.address || '—' }}</dd>
              <dt>Observações</dt><dd>{{ c.notes || '—' }}</dd>
            </dl>
          </section>
        </div>
      }
    </div>
    @if (editing() && customer()) {
      <nx-customer-form [customer]="customer()" (closed)="editing.set(false)" (saved)="onSaved($event)" />
    }
  `,
})
export class CustomerDetailPage implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly toast = inject(ToastService);
  private readonly confirm = inject(ConfirmService);
  protected readonly session = inject(SessionService);
  protected readonly statuses = SALE_STATUS;

  readonly id = input.required<string>();
  protected readonly customer = signal<CustomerDetail | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly editing = signal(false);

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.error.set(null);
    this.http.get<CustomerDetail>(`/api/customers/${this.id()}`).subscribe({
      next: (customer) => this.customer.set(customer),
      error: (error) => this.error.set(errorMessage(error)),
    });
  }

  protected onSaved(customer: CustomerDetail): void {
    this.customer.set(customer);
    this.editing.set(false);
    this.toast.success('Cliente atualizado.');
  }

  protected async toggle(): Promise<void> {
    const current = this.customer();
    if (!current) return;
    if (current.active && !(await this.confirm.confirm({
      title: 'Desativar cliente',
      message: 'O cliente não poderá ser selecionado em novas vendas. O histórico é mantido.',
      confirmText: 'Desativar',
      tone: 'danger',
    }))) return;
    this.http.put<CustomerDetail>(`/api/customers/${current.id}/active`, { active: !current.active }).subscribe({
      next: (customer) => this.customer.set(customer),
      error: (error) => this.toast.error(errorMessage(error)),
    });
  }
}
