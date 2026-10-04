import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { SessionService } from '../../core/auth/session.service';
import { STOCK_SITUATION } from '../../core/i18n/labels';
import { Category, Page, Product } from '../../core/models/api.models';
import { ConfirmService } from '../../core/ui/confirm.service';
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

@Component({
  selector: 'nx-products-list-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CurrencyPipe,
    DecimalPipe,
    FormsModule,
    RouterLink,
    BadgeComponent,
    ButtonComponent,
    IconComponent,
    EmptyStateComponent,
    ErrorStateComponent,
    PageHeaderComponent,
    PaginationComponent,
    SkeletonComponent,
  ],
  template: `
    <div class="nx-page">
      <nx-page-header title="Produtos" subtitle="Cadastro de produtos, preços e estoque mínimo">
        @if (session.hasPermission('PRODUCT_CREATE')) {
          <a nxButton routerLink="/produtos/novo"><nx-icon name="plus" [size]="16" /> Novo produto</a>
        }
      </nx-page-header>

      <div class="nx-toolbar">
        <div class="nx-search">
          <nx-icon name="search" [size]="16" />
          <input class="nx-control" type="search" placeholder="Buscar por nome, SKU ou código" [ngModel]="query()"
                 (ngModelChange)="onQuery($event)" aria-label="Buscar produtos" />
        </div>
        @if (categories().length) {
          <select class="nx-control" [ngModel]="categoryId()" (ngModelChange)="categoryId.set($event); reload()" aria-label="Categoria">
            <option value="">Todas as categorias</option>
            @for (category of categories(); track category.id) {
              <option [value]="category.id">{{ category.name }}</option>
            }
          </select>
        }
        <select class="nx-control" [ngModel]="situation()" (ngModelChange)="situation.set($event); reload()" aria-label="Situação do estoque">
          <option value="">Qualquer estoque</option>
          <option value="NORMAL">Normal</option>
          <option value="LOW_STOCK">Baixo</option>
          <option value="OUT_OF_STOCK">Sem estoque</option>
        </select>
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
            <div class="nx-table-scroll">
              <table class="nx-table nx-table--stack">
                <thead>
                  <tr>
                    <th>Produto</th><th>Categoria</th><th class="num">Preço</th>
                    @if (costView()) { <th class="num">Custo</th> }
                    <th class="num">Estoque</th><th>Status</th><th></th>
                  </tr>
                </thead>
                <tbody>
                  @for (product of p.content; track product.id) {
                    <tr>
                      <td class="nx-stack-main">
                        <a class="nx-table__main" [routerLink]="['/produtos', product.id]">{{ product.name }}</a>
                        <span class="nx-table__sub">{{ product.sku ? 'SKU ' + product.sku : product.barcode || '—' }}</span>
                      </td>
                      <td data-label="Categoria">{{ product.categoryName || '—' }}</td>
                      <td class="num" data-label="Preço">{{ product.salePrice | currency }}</td>
                      @if (costView()) { <td class="num" data-label="Custo">{{ product.costPrice | currency }}</td> }
                      <td class="num" data-label="Estoque">
                        {{ product.currentStock | number: '1.0-3' }} {{ product.unit }}
                        <nx-badge [tone]="situation_[product.situation].tone">{{ situation_[product.situation].label }}</nx-badge>
                      </td>
                      <td><nx-badge [tone]="product.active ? 'success' : 'neutral'">{{ product.active ? 'Ativo' : 'Inativo' }}</nx-badge></td>
                      <td class="nx-table__actions">
                        <a class="nx-icon-button" [routerLink]="['/produtos', product.id]" aria-label="Editar"><nx-icon name="edit" [size]="16" /></a>
                        @if (session.hasPermission('PRODUCT_DISABLE')) {
                          <button type="button" class="nx-icon-button" [class.is-danger]="product.active" (click)="toggle(product)"
                                  [attr.aria-label]="product.active ? 'Desativar' : 'Ativar'" [title]="product.active ? 'Desativar' : 'Ativar'">
                            <nx-icon [name]="product.active ? 'power' : 'check-circle'" [size]="16" />
                          </button>
                        }
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
            <nx-pagination [page]="p.page" [totalPages]="p.totalPages" [totalElements]="p.totalElements" (pageChange)="goTo($event)" />
          } @else {
            <nx-empty-state icon="tag" title="Nenhum produto encontrado" message="Cadastre produtos ou ajuste os filtros.">
              @if (session.hasPermission('PRODUCT_CREATE')) {
                <a nxButton size="sm" routerLink="/produtos/novo">Cadastrar produto</a>
              }
            </nx-empty-state>
          }
        }
      </div>
    </div>
  `,
})
export class ProductsListPage implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);
  protected readonly session = inject(SessionService);
  protected readonly situation_ = STOCK_SITUATION;

  protected readonly query = signal('');
  protected readonly categoryId = signal('');
  protected readonly situation = signal('');
  protected readonly active = signal('true');
  protected readonly pageIndex = signal(0);
  protected readonly page = signal<Page<Product> | null>(null);
  protected readonly categories = signal<Category[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly costView = computed(() => this.session.hasPermission('PRODUCT_COST_VIEW'));
  private timer: ReturnType<typeof setTimeout> | null = null;

  ngOnInit(): void {
    this.load();
    if (this.session.hasFeature('CATEGORIES')) {
      this.http.get<Category[]>('/api/categories').subscribe({ next: (list) => this.categories.set(list), error: () => undefined });
    }
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
      .get<Page<Product>>('/api/products', {
        params: params({
          q: this.query().trim(),
          categoryId: this.categoryId(),
          situation: this.situation(),
          active: this.active(),
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

  protected async toggle(product: Product): Promise<void> {
    if (product.active) {
      const ok = await this.confirm.confirm({
        title: 'Desativar produto',
        message: `"${product.name}" deixará de aparecer no PDV. O histórico de vendas é mantido.`,
        confirmText: 'Desativar',
        tone: 'danger',
      });
      if (!ok) return;
    }
    this.http.put<Product>(`/api/products/${product.id}/active`, { active: !product.active }).subscribe({
      next: () => {
        this.toast.success(product.active ? 'Produto desativado.' : 'Produto ativado.');
        this.load();
      },
      error: (error) => this.toast.error(errorMessage(error)),
    });
  }
}
