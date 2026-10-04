import { DecimalPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { SessionService } from '../../core/auth/session.service';
import { STOCK_SITUATION } from '../../core/i18n/labels';
import { Page, StockItem, StockSituation, StockSummary } from '../../core/models/api.models';
import { ToastService } from '../../core/ui/toast.service';
import { errorMessage } from '../../core/util/api-error';
import { params } from '../../core/util/http-params';
import { AlertComponent } from '../../shared/ui/alert/alert.component';
import { BadgeComponent } from '../../shared/ui/badge/badge.component';
import { ButtonComponent } from '../../shared/ui/button/button.component';
import { IconComponent } from '../../shared/ui/icon/icon.component';
import { ModalComponent } from '../../shared/ui/modal/modal.component';
import {
  EmptyStateComponent,
  ErrorStateComponent,
  PageHeaderComponent,
  PaginationComponent,
  SkeletonComponent,
} from '../../shared/ui/states/states.components';

type StockAction = { kind: 'entry' | 'adjust'; item: StockItem };

@Component({
  selector: 'nx-stock-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DecimalPipe, FormsModule, RouterLink, AlertComponent, BadgeComponent, ButtonComponent, IconComponent, ModalComponent, EmptyStateComponent, ErrorStateComponent, PageHeaderComponent, PaginationComponent, SkeletonComponent],
  template: `
    <div class="nx-page">
      <nx-page-header title="Controle de estoque" subtitle="Saiba o que entra e o que sai">
        @if (session.hasPermission('STOCK_MOVEMENT_READ')) {
          <a nxButton variant="secondary" routerLink="/estoque/movimentacoes"><nx-icon name="repeat" [size]="16" /> Movimentações</a>
        }
      </nx-page-header>

      <div class="nx-chips">
        <button type="button" class="nx-chip" [class.is-active]="!situation()" (click)="filter(null)">Todos</button>
        <button type="button" class="nx-chip" [class.is-active]="situation() === 'NORMAL'" (click)="filter('NORMAL')">Normal</button>
        <button type="button" class="nx-chip" [class.is-active]="situation() === 'LOW_STOCK'" (click)="filter('LOW_STOCK')">
          Estoque baixo <span class="nx-chip__count">{{ summary()?.lowStock ?? 0 }}</span>
        </button>
        <button type="button" class="nx-chip" [class.is-active]="situation() === 'OUT_OF_STOCK'" (click)="filter('OUT_OF_STOCK')">
          Sem estoque <span class="nx-chip__count">{{ summary()?.outOfStock ?? 0 }}</span>
        </button>
      </div>

      <div class="nx-toolbar">
        <div class="nx-search">
          <nx-icon name="search" [size]="16" />
          <input class="nx-control" type="search" placeholder="Buscar produto" [ngModel]="query()" (ngModelChange)="onQuery($event)" />
        </div>
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
                <thead><tr><th>Produto</th><th class="num">Estoque atual</th><th class="num">Estoque mínimo</th><th>Status</th><th></th></tr></thead>
                <tbody>
                  @for (item of p.content; track item.productId) {
                    <tr>
                      <td class="nx-stack-main">
                        <span class="nx-table__main">{{ item.name }}</span>
                        <span class="nx-table__sub">{{ item.categoryName || 'Sem categoria' }}{{ item.sku ? ' • SKU ' + item.sku : '' }}</span>
                      </td>
                      <td class="num" data-label="Atual" [class.text-danger]="item.situation === 'OUT_OF_STOCK'" [class.text-warning]="item.situation === 'LOW_STOCK'">
                        {{ item.currentStock | number: '1.0-3' }} {{ item.unit }}
                      </td>
                      <td class="num" data-label="Mínimo">{{ item.minimumStock | number: '1.0-3' }}</td>
                      <td><nx-badge [tone]="situations[item.situation].tone">{{ situations[item.situation].label }}</nx-badge></td>
                      <td class="nx-table__actions">
                        @if (session.hasPermission('STOCK_ENTRY')) {
                          <button nxButton size="sm" variant="secondary" type="button" (click)="openAction('entry', item)">
                            <nx-icon name="plus" [size]="14" /> Entrada
                          </button>
                        }
                        @if (session.hasPermission('STOCK_ADJUST')) {
                          <button nxButton size="sm" variant="ghost" type="button" (click)="openAction('adjust', item)">Ajustar</button>
                        }
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
            <nx-pagination [page]="p.page" [totalPages]="p.totalPages" [totalElements]="p.totalElements" (pageChange)="goTo($event)" />
          } @else {
            <nx-empty-state icon="package" title="Nenhum produto nesta situação" />
          }
        }
      </div>
    </div>

    @if (action(); as a) {
      <nx-modal [title]="a.kind === 'entry' ? 'Registrar entrada' : 'Ajustar estoque'" [subtitle]="a.item.name" size="sm" (closed)="action.set(null)">
        <p class="text-muted text-small" style="margin-bottom: 1rem">
          Estoque atual: <strong>{{ a.item.currentStock | number: '1.0-3' }} {{ a.item.unit }}</strong>
        </p>
        <label class="nx-label" for="s-qty">{{ a.kind === 'entry' ? 'Quantidade recebida' : 'Nova quantidade (contagem)' }}</label>
        <input id="s-qty" class="nx-control" type="number" min="0" step="0.001" [ngModel]="quantity()" (ngModelChange)="quantity.set($event)" />
        <label class="nx-label" for="s-reason" style="margin-top: 1rem">{{ a.kind === 'adjust' ? 'Motivo *' : 'Observação' }}</label>
        <input id="s-reason" class="nx-control" maxlength="300" [ngModel]="reason()" (ngModelChange)="reason.set($event)"
               [placeholder]="a.kind === 'adjust' ? 'Ex.: contagem de inventário, avaria' : 'Ex.: NF 1234'" />
        @if (actionError(); as message) { <nx-alert tone="danger" style="margin-top: 1rem">{{ message }}</nx-alert> }
        <ng-container modalFooter>
          <button nxButton variant="secondary" type="button" (click)="action.set(null)">Cancelar</button>
          <button nxButton type="button" [loading]="saving()" (click)="submitAction(a)">Confirmar</button>
        </ng-container>
      </nx-modal>
    }
  `,
})
export class StockPage implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly toast = inject(ToastService);
  private readonly route = inject(ActivatedRoute);
  protected readonly session = inject(SessionService);
  protected readonly situations = STOCK_SITUATION;

  protected readonly query = signal('');
  protected readonly situation = signal<StockSituation | null>(
    (this.route.snapshot.queryParamMap.get('situacao') as StockSituation | null) ?? null,
  );
  protected readonly pageIndex = signal(0);
  protected readonly page = signal<Page<StockItem> | null>(null);
  protected readonly summary = signal<StockSummary | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  protected readonly action = signal<StockAction | null>(null);
  protected readonly quantity = signal<number | null>(null);
  protected readonly reason = signal('');
  protected readonly saving = signal(false);
  protected readonly actionError = signal<string | null>(null);
  private timer: ReturnType<typeof setTimeout> | null = null;

  ngOnInit(): void {
    this.load();
  }

  protected filter(situation: StockSituation | null): void {
    this.situation.set(situation);
    this.pageIndex.set(0);
    this.load();
  }

  protected onQuery(value: string): void {
    this.query.set(value);
    if (this.timer) clearTimeout(this.timer);
    this.timer = setTimeout(() => {
      this.pageIndex.set(0);
      this.load();
    }, 250);
  }

  protected goTo(page: number): void {
    this.pageIndex.set(page);
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.http.get<StockSummary>('/api/stock/summary').subscribe({ next: (s) => this.summary.set(s), error: () => undefined });
    this.http
      .get<Page<StockItem>>('/api/stock', {
        params: params({ q: this.query().trim(), situation: this.situation(), page: this.pageIndex(), size: 20 }),
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

  protected openAction(kind: 'entry' | 'adjust', item: StockItem): void {
    this.quantity.set(kind === 'adjust' ? item.currentStock : null);
    this.reason.set('');
    this.actionError.set(null);
    this.action.set({ kind, item });
  }

  protected submitAction(action: StockAction): void {
    const quantity = Number(this.quantity());
    if (Number.isNaN(quantity) || quantity < 0 || (action.kind === 'entry' && quantity <= 0)) {
      this.actionError.set('Informe uma quantidade válida.');
      return;
    }
    if (action.kind === 'adjust' && this.reason().trim().length < 3) {
      this.actionError.set('Informe o motivo do ajuste.');
      return;
    }
    this.saving.set(true);
    const call = action.kind === 'entry'
      ? this.http.post('/api/stock/entries', { productId: action.item.productId, quantity, reason: this.reason() || null })
      : this.http.post('/api/stock/adjustments', { productId: action.item.productId, newQuantity: quantity, reason: this.reason() });
    call.subscribe({
      next: () => {
        this.saving.set(false);
        this.action.set(null);
        this.toast.success(action.kind === 'entry' ? 'Entrada registrada.' : 'Estoque ajustado.');
        this.load();
      },
      error: (error) => {
        this.saving.set(false);
        this.actionError.set(errorMessage(error));
      },
    });
  }
}
