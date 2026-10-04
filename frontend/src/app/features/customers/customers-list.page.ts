import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { SessionService } from '../../core/auth/session.service';
import { CustomerDetail, CustomerSummary, Page } from '../../core/models/api.models';
import { ToastService } from '../../core/ui/toast.service';
import { errorMessage } from '../../core/util/api-error';
import { params } from '../../core/util/http-params';
import { BadgeComponent } from '../../shared/ui/badge/badge.component';
import { ButtonComponent } from '../../shared/ui/button/button.component';
import { IconComponent } from '../../shared/ui/icon/icon.component';
import {
  EmptyStateComponent,
  ErrorStateComponent,
  PageHeaderComponent,
  PaginationComponent,
  SkeletonComponent,
} from '../../shared/ui/states/states.components';
import { CustomerFormComponent } from './customer-form.component';

@Component({
  selector: 'nx-customers-list-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink, BadgeComponent, ButtonComponent, IconComponent, EmptyStateComponent, ErrorStateComponent, PageHeaderComponent, PaginationComponent, SkeletonComponent, CustomerFormComponent],
  template: `
    <div class="nx-page">
      <nx-page-header title="Clientes" subtitle="Cadastro e histórico de compras">
        @if (session.hasPermission('CUSTOMER_CREATE')) {
          <button nxButton type="button" (click)="creating.set(true)"><nx-icon name="plus" [size]="16" /> Novo cliente</button>
        }
      </nx-page-header>
      <div class="nx-toolbar">
        <div class="nx-search">
          <nx-icon name="search" [size]="16" />
          <input class="nx-control" type="search" placeholder="Nome, documento, telefone ou e-mail" [ngModel]="query()" (ngModelChange)="onQuery($event)" />
        </div>
        <select class="nx-control" [ngModel]="active()" (ngModelChange)="active.set($event); reload()" aria-label="Status">
          <option value="true">Ativos</option>
          <option value="false">Inativos</option>
          <option value="">Todos</option>
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
              <thead><tr><th>Cliente</th><th>Documento</th><th>Telefone</th><th>E-mail</th><th>Status</th><th></th></tr></thead>
              <tbody>
                @for (customer of p.content; track customer.id) {
                  <tr>
                    <td class="nx-stack-main"><a class="nx-table__main" [routerLink]="['/clientes', customer.id]">{{ customer.name }}</a></td>
                    <td data-label="Documento">{{ customer.documentMasked || '—' }}</td>
                    <td data-label="Telefone">{{ customer.phone || '—' }}</td>
                    <td data-label="E-mail">{{ customer.email || '—' }}</td>
                    <td><nx-badge [tone]="customer.active ? 'success' : 'neutral'">{{ customer.active ? 'Ativo' : 'Inativo' }}</nx-badge></td>
                    <td class="nx-table__actions">
                      <a class="nx-icon-button" [routerLink]="['/clientes', customer.id]" aria-label="Ver cliente"><nx-icon name="eye" [size]="16" /></a>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
            <nx-pagination [page]="p.page" [totalPages]="p.totalPages" [totalElements]="p.totalElements" (pageChange)="goTo($event)" />
          } @else {
            <nx-empty-state icon="users" title="Nenhum cliente encontrado" />
          }
        }
      </div>
    </div>
    @if (creating()) {
      <nx-customer-form (closed)="creating.set(false)" (saved)="onSaved($event)" />
    }
  `,
})
export class CustomersListPage implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);
  protected readonly session = inject(SessionService);

  protected readonly query = signal('');
  protected readonly active = signal('true');
  protected readonly pageIndex = signal(0);
  protected readonly page = signal<Page<CustomerSummary> | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly creating = signal(false);
  private timer: ReturnType<typeof setTimeout> | null = null;

  ngOnInit(): void {
    this.load();
  }

  protected onQuery(value: string): void {
    this.query.set(value);
    if (this.timer) clearTimeout(this.timer);
    this.timer = setTimeout(() => this.reload(), 250);
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
      .get<Page<CustomerSummary>>('/api/customers', {
        params: params({ q: this.query().trim(), active: this.active(), page: this.pageIndex(), size: 20 }),
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

  protected onSaved(customer: CustomerDetail): void {
    this.creating.set(false);
    this.toast.success('Cliente cadastrado.');
    void this.router.navigate(['/clientes', customer.id]);
  }
}
