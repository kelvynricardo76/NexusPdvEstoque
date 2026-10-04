import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { SessionService } from '../../core/auth/session.service';
import { FINANCIAL_STATUS, FINANCIAL_TYPE_LABELS } from '../../core/i18n/labels';
import { FinancialEntry, FinancialSummary, FinancialType, Page } from '../../core/models/api.models';
import { ConfirmService } from '../../core/ui/confirm.service';
import { ToastService } from '../../core/ui/toast.service';
import { errorMessage } from '../../core/util/api-error';
import { params, todayIso } from '../../core/util/http-params';
import { AlertComponent } from '../../shared/ui/alert/alert.component';
import { BadgeComponent } from '../../shared/ui/badge/badge.component';
import { ButtonComponent } from '../../shared/ui/button/button.component';
import { IconComponent } from '../../shared/ui/icon/icon.component';
import { ModalComponent } from '../../shared/ui/modal/modal.component';
import { PeriodFilterComponent, periodParams, PeriodValue } from '../../shared/ui/period-filter/period-filter.component';
import {
  EmptyStateComponent,
  ErrorStateComponent,
  PageHeaderComponent,
  PaginationComponent,
  SkeletonComponent,
} from '../../shared/ui/states/states.components';
import { StatCardComponent } from '../../shared/ui/stat-card/stat-card.component';

/** Contas a pagar e a receber do cliente — financeiro operacional (não é a cobrança da Nexus). */
@Component({
  selector: 'nx-financial-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CurrencyPipe, DatePipe, FormsModule, ReactiveFormsModule, AlertComponent, BadgeComponent, ButtonComponent, IconComponent, ModalComponent, PeriodFilterComponent, EmptyStateComponent, ErrorStateComponent, PageHeaderComponent, PaginationComponent, SkeletonComponent, StatCardComponent],
  template: `
    <div class="nx-page">
      <nx-page-header title="Financeiro" subtitle="Contas a pagar e a receber">
        @if (session.hasPermission('FINANCIAL_CREATE')) {
          <button nxButton type="button" (click)="open(null)"><nx-icon name="plus" [size]="16" /> Nova conta</button>
        }
      </nx-page-header>

      <div class="nx-toolbar">
        <span class="text-muted text-small">Resumo do período</span>
        <nx-period-filter [value]="summaryPeriod()" (valueChange)="summaryPeriod.set($event); loadSummary()" />
      </div>
      @if (summary(); as s) {
        <div class="nx-grid nx-grid--kpi">
          <nx-stat-card icon="trending-up" label="Receitas (pagas)" [value]="(s.revenue | currency) ?? ''" />
          <nx-stat-card icon="trending-down" label="Despesas (pagas)" [value]="(s.expenses | currency) ?? ''" />
          <nx-stat-card icon="wallet" label="A receber" [value]="(s.receivablePending | currency) ?? ''"
                        [hint]="s.receivableOverdueCount ? s.receivableOverdueCount + ' vencida(s)' : s.receivablePendingCount + ' em aberto'"
                        [hintTone]="s.receivableOverdueCount ? 'warning' : 'muted'" />
          <nx-stat-card icon="credit-card" label="A pagar" [value]="(s.payablePending | currency) ?? ''"
                        [hint]="s.payableOverdueCount ? s.payableOverdueCount + ' vencida(s)' : s.payablePendingCount + ' em aberto'"
                        [hintTone]="s.payableOverdueCount ? 'danger' : 'muted'" />
        </div>
      }

      <div class="nx-tabs">
        <button type="button" [class.is-active]="!type()" (click)="setType(null)">Todas</button>
        <button type="button" [class.is-active]="type() === 'PAYABLE'" (click)="setType('PAYABLE')">A pagar</button>
        <button type="button" [class.is-active]="type() === 'RECEIVABLE'" (click)="setType('RECEIVABLE')">A receber</button>
      </div>
      <div class="nx-toolbar">
        <div class="nx-search">
          <nx-icon name="search" [size]="16" />
          <input class="nx-control" type="search" placeholder="Descrição ou categoria" [ngModel]="query()" (ngModelChange)="query.set($event)" (keydown.enter)="reload()" />
        </div>
        <select class="nx-control" [ngModel]="status()" (ngModelChange)="status.set($event); reload()" aria-label="Status">
          <option value="">Todos os status</option>
          <option value="PENDING">A vencer</option>
          <option value="OVERDUE">Vencidas</option>
          <option value="PAID">Pagas</option>
          <option value="CANCELED">Canceladas</option>
        </select>
        <input class="nx-control" type="date" [ngModel]="from()" (ngModelChange)="from.set($event); reload()" aria-label="Vencimento de" />
        <input class="nx-control" type="date" [ngModel]="to()" (ngModelChange)="to.set($event); reload()" aria-label="Vencimento até" />
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
                <thead><tr><th>Descrição</th><th>Tipo</th><th>Vencimento</th><th class="num">Valor</th><th>Status</th><th></th></tr></thead>
                <tbody>
                  @for (entry of p.content; track entry.id) {
                    <tr>
                      <td class="nx-stack-main">
                        <span class="nx-table__main">{{ entry.description }}</span>
                        <span class="nx-table__sub">{{ entry.category || 'Sem categoria' }}{{ entry.customerName ? ' • ' + entry.customerName : '' }}{{ entry.supplierName ? ' • ' + entry.supplierName : '' }}</span>
                      </td>
                      <td data-label="Tipo">{{ typeLabels[entry.type] }}</td>
                      <td data-label="Vencimento">{{ entry.dueDate | date: 'dd/MM/yyyy' }}
                        @if (entry.paymentDate) { <span class="nx-table__sub">Pago em {{ entry.paymentDate | date: 'dd/MM/yyyy' }}</span> }
                      </td>
                      <td class="num" data-label="Valor" [class.text-danger]="entry.type === 'PAYABLE'" [class.text-primary]="entry.type === 'RECEIVABLE'">
                        {{ entry.amount | currency }}
                      </td>
                      <td><nx-badge [tone]="statuses[entry.status].tone">{{ statuses[entry.status].label }}</nx-badge></td>
                      <td class="nx-table__actions">
                        @if ((entry.status === 'PENDING' || entry.status === 'OVERDUE') && session.hasPermission('FINANCIAL_UPDATE')) {
                          <button nxButton size="sm" variant="secondary" type="button" (click)="pay(entry)">
                            <nx-icon name="check" [size]="14" /> {{ entry.type === 'PAYABLE' ? 'Pagar' : 'Receber' }}
                          </button>
                          <button type="button" class="nx-icon-button" (click)="open(entry)" aria-label="Editar"><nx-icon name="edit" [size]="16" /></button>
                        }
                        @if ((entry.status === 'PENDING' || entry.status === 'OVERDUE') && session.hasPermission('FINANCIAL_CANCEL')) {
                          <button type="button" class="nx-icon-button is-danger" (click)="cancel(entry)" aria-label="Cancelar"><nx-icon name="x-circle" [size]="16" /></button>
                        }
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
            <nx-pagination [page]="p.page" [totalPages]="p.totalPages" [totalElements]="p.totalElements" (pageChange)="goTo($event)" />
          } @else {
            <nx-empty-state icon="wallet" title="Nenhuma conta encontrada" message="Ajuste os filtros de vencimento e status." />
          }
        }
      </div>
    </div>

    @if (editing() !== undefined) {
      <nx-modal [title]="editing() ? 'Editar conta' : 'Nova conta'" (closed)="editing.set(undefined)">
        <form id="entry-form" class="nx-form-grid" [formGroup]="form" (ngSubmit)="save()" novalidate>
          <div>
            <label class="nx-label" for="f-type">Tipo *</label>
            <select id="f-type" class="nx-control" formControlName="type">
              <option value="PAYABLE">A pagar (despesa)</option>
              <option value="RECEIVABLE">A receber (receita)</option>
            </select>
          </div>
          <div>
            <label class="nx-label" for="f-category">Categoria</label>
            <input id="f-category" class="nx-control" formControlName="category" maxlength="80" placeholder="Ex.: Aluguel, Fornecedores" />
          </div>
          <div class="nx-form-grid__full">
            <label class="nx-label" for="f-desc">Descrição *</label>
            <input id="f-desc" class="nx-control" formControlName="description" maxlength="200" />
          </div>
          <div>
            <label class="nx-label" for="f-amount">Valor (R$) *</label>
            <input id="f-amount" class="nx-control" type="number" min="0.01" step="0.01" formControlName="amount" />
          </div>
          <div>
            <label class="nx-label" for="f-due">Vencimento *</label>
            <input id="f-due" class="nx-control" type="date" formControlName="dueDate" />
          </div>
          <div class="nx-form-grid__full">
            <label class="nx-label" for="f-notes">Observações</label>
            <textarea id="f-notes" class="nx-control" rows="2" formControlName="notes" maxlength="1000"></textarea>
          </div>
          @if (formError(); as message) { <nx-alert class="nx-form-grid__full" tone="danger">{{ message }}</nx-alert> }
        </form>
        <ng-container modalFooter>
          <button nxButton variant="secondary" type="button" (click)="editing.set(undefined)">Cancelar</button>
          <button nxButton type="submit" form="entry-form" [loading]="saving()">Salvar</button>
        </ng-container>
      </nx-modal>
    }
  `,
})
export class FinancialPage implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly fb = inject(FormBuilder);
  private readonly toast = inject(ToastService);
  private readonly confirm = inject(ConfirmService);
  private readonly route = inject(ActivatedRoute);
  protected readonly session = inject(SessionService);
  protected readonly statuses = FINANCIAL_STATUS;
  protected readonly typeLabels = FINANCIAL_TYPE_LABELS;

  protected readonly summaryPeriod = signal<PeriodValue>({ preset: 'LAST_30_DAYS' });
  protected readonly summary = signal<FinancialSummary | null>(null);
  protected readonly type = signal<FinancialType | null>((this.route.snapshot.queryParamMap.get('tipo') as FinancialType | null) ?? null);
  protected readonly status = signal(this.route.snapshot.queryParamMap.get('status') ?? '');
  protected readonly query = signal('');
  protected readonly from = signal('');
  protected readonly to = signal('');
  protected readonly pageIndex = signal(0);
  protected readonly page = signal<Page<FinancialEntry> | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  protected readonly editing = signal<FinancialEntry | null | undefined>(undefined);
  protected readonly saving = signal(false);
  protected readonly formError = signal<string | null>(null);
  protected readonly form = this.fb.nonNullable.group({
    type: ['PAYABLE' as FinancialType, Validators.required],
    description: ['', [Validators.required, Validators.maxLength(200)]],
    category: [''],
    amount: [0, [Validators.required, Validators.min(0.01)]],
    dueDate: [todayIso(), Validators.required],
    notes: [''],
  });

  ngOnInit(): void {
    this.loadSummary();
    this.load();
  }

  protected setType(type: FinancialType | null): void {
    this.type.set(type);
    this.reload();
  }

  protected reload(): void {
    this.pageIndex.set(0);
    this.load();
  }

  protected goTo(page: number): void {
    this.pageIndex.set(page);
    this.load();
  }

  protected loadSummary(): void {
    this.http.get<FinancialSummary>('/api/financial/summary', { params: params(periodParams(this.summaryPeriod())) }).subscribe({
      next: (summary) => this.summary.set(summary),
      error: () => undefined,
    });
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.http
      .get<Page<FinancialEntry>>('/api/financial/entries', {
        params: params({
          type: this.type(),
          status: this.status(),
          q: this.query().trim(),
          from: this.from(),
          to: this.to(),
          page: this.pageIndex(),
          size: 20,
        }),
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

  protected open(entry: FinancialEntry | null): void {
    this.formError.set(null);
    this.form.reset({
      type: entry?.type ?? this.type() ?? 'PAYABLE',
      description: entry?.description ?? '',
      category: entry?.category ?? '',
      amount: entry?.amount ?? 0,
      dueDate: entry?.dueDate ?? todayIso(),
      notes: entry?.notes ?? '',
    });
    if (entry) this.form.controls.type.disable();
    else this.form.controls.type.enable();
    this.editing.set(entry);
  }

  protected save(): void {
    if (this.form.invalid) {
      this.formError.set('Preencha descrição, valor e vencimento.');
      return;
    }
    const value = this.form.getRawValue();
    const body = { ...value, category: value.category || null, notes: value.notes || null };
    const current = this.editing();
    this.saving.set(true);
    const call = current
      ? this.http.put<FinancialEntry>(`/api/financial/entries/${current.id}`, body)
      : this.http.post<FinancialEntry>('/api/financial/entries', body);
    call.subscribe({
      next: () => {
        this.saving.set(false);
        this.editing.set(undefined);
        this.toast.success('Conta salva.');
        this.load();
        this.loadSummary();
      },
      error: (error) => {
        this.saving.set(false);
        this.formError.set(errorMessage(error));
      },
    });
  }

  protected async pay(entry: FinancialEntry): Promise<void> {
    const ok = await this.confirm.confirm({
      title: entry.type === 'PAYABLE' ? 'Registrar pagamento' : 'Registrar recebimento',
      message: `Baixar "${entry.description}" com data de hoje?`,
      confirmText: 'Confirmar',
    });
    if (!ok) return;
    this.http.post(`/api/financial/entries/${entry.id}/pay`, { paymentDate: todayIso() }).subscribe({
      next: () => {
        this.toast.success('Baixa registrada.');
        this.load();
        this.loadSummary();
      },
      error: (error) => this.toast.error(errorMessage(error)),
    });
  }

  protected async cancel(entry: FinancialEntry): Promise<void> {
    const ok = await this.confirm.confirm({
      title: 'Cancelar conta',
      message: `Cancelar "${entry.description}"? A conta será mantida no histórico como cancelada.`,
      confirmText: 'Cancelar conta',
      tone: 'danger',
    });
    if (!ok) return;
    this.http.post(`/api/financial/entries/${entry.id}/cancel`, {}).subscribe({
      next: () => {
        this.toast.success('Conta cancelada.');
        this.load();
        this.loadSummary();
      },
      error: (error) => this.toast.error(errorMessage(error)),
    });
  }
}
