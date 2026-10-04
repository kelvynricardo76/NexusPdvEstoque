import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, input, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { SessionService } from '../../core/auth/session.service';
import { UNIT_LABELS } from '../../core/i18n/labels';
import { Category, Page, Product, ProductRequest, ProductUnit, Supplier } from '../../core/models/api.models';
import { ToastService } from '../../core/ui/toast.service';
import { errorMessage } from '../../core/util/api-error';
import { params } from '../../core/util/http-params';
import { AlertComponent } from '../../shared/ui/alert/alert.component';
import { ButtonComponent } from '../../shared/ui/button/button.component';
import { IconComponent } from '../../shared/ui/icon/icon.component';
import { ErrorStateComponent, PageHeaderComponent, SkeletonComponent } from '../../shared/ui/states/states.components';

/** Cadastro/edição de produto. O estoque só é alterado pelo módulo de estoque após a criação. */
@Component({
  selector: 'nx-product-form-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, AlertComponent, ButtonComponent, IconComponent, ErrorStateComponent, PageHeaderComponent, SkeletonComponent],
  template: `
    <div class="nx-page">
      <nx-page-header [title]="isNew() ? 'Novo produto' : 'Editar produto'" [subtitle]="product()?.name ?? 'Preencha os dados do produto'">
        <a nxButton variant="secondary" routerLink="/produtos"><nx-icon name="arrow-left" [size]="16" /> Voltar</a>
      </nx-page-header>

      @if (loadError(); as message) {
        <nx-error-state [message]="message" (retry)="load()" />
      } @else if (!isNew() && !product()) {
        <nx-skeleton [rows]="8" />
      } @else {
        <form class="nx-panel" [formGroup]="form" (ngSubmit)="save()" novalidate>
          <div class="nx-form-grid">
            <div class="nx-form-grid__full">
              <label class="nx-label" for="p-name">Nome *</label>
              <input id="p-name" class="nx-control" formControlName="name" maxlength="150" />
              @if (form.controls.name.touched && form.controls.name.invalid) { <p class="nx-field-error">Informe o nome.</p> }
            </div>
            <div>
              <label class="nx-label" for="p-sku">SKU</label>
              <input id="p-sku" class="nx-control" formControlName="sku" maxlength="60" />
            </div>
            <div>
              <label class="nx-label" for="p-barcode">Código de barras</label>
              <input id="p-barcode" class="nx-control" formControlName="barcode" maxlength="60" inputmode="numeric" />
            </div>
            <div>
              <label class="nx-label" for="p-category">Categoria</label>
              <select id="p-category" class="nx-control" formControlName="categoryId">
                <option value="">Sem categoria</option>
                @for (category of categories(); track category.id) { <option [value]="category.id">{{ category.name }}</option> }
              </select>
            </div>
            @if (canSeeSuppliers()) {
              <div>
                <label class="nx-label" for="p-supplier">Fornecedor</label>
                <select id="p-supplier" class="nx-control" formControlName="supplierId">
                  <option value="">Sem fornecedor</option>
                  @for (supplier of suppliers(); track supplier.id) { <option [value]="supplier.id">{{ supplier.tradeName || supplier.legalName }}</option> }
                </select>
              </div>
            }
            <div>
              <label class="nx-label" for="p-price">Preço de venda (R$) *</label>
              <input id="p-price" class="nx-control" type="number" min="0" step="0.01" formControlName="salePrice" />
              @if (form.controls.salePrice.touched && form.controls.salePrice.invalid) { <p class="nx-field-error">Informe um preço válido.</p> }
            </div>
            @if (costView()) {
              <div>
                <label class="nx-label" for="p-cost">Preço de custo (R$)</label>
                <input id="p-cost" class="nx-control" type="number" min="0" step="0.01" formControlName="costPrice" />
              </div>
            }
            <div>
              <label class="nx-label" for="p-unit">Unidade</label>
              <select id="p-unit" class="nx-control" formControlName="unit">
                @for (unit of units; track unit) { <option [value]="unit">{{ unit }} — {{ unitLabels[unit] }}</option> }
              </select>
            </div>
            <div>
              <label class="nx-label" for="p-min">Estoque mínimo</label>
              <input id="p-min" class="nx-control" type="number" min="0" step="0.001" formControlName="minimumStock" />
            </div>
            @if (isNew()) {
              <div>
                <label class="nx-label" for="p-initial">Estoque inicial</label>
                <input id="p-initial" class="nx-control" type="number" min="0" step="0.001" formControlName="initialStock" />
                <p class="nx-hint">Gera uma movimentação de estoque inicial.</p>
              </div>
            } @else {
              <div>
                <span class="nx-label">Estoque atual</span>
                <p>{{ product()?.currentStock }} {{ product()?.unit }} <span class="text-muted text-small">— altere pelo módulo de estoque</span></p>
              </div>
            }
            <div class="nx-form-grid__full">
              <label class="nx-label" for="p-desc">Descrição</label>
              <textarea id="p-desc" class="nx-control" rows="3" formControlName="description" maxlength="1000"></textarea>
            </div>
          </div>

          @if (error(); as message) { <nx-alert tone="danger">{{ message }}</nx-alert> }

          @if (canEdit()) {
            <div class="nx-actions">
              <a nxButton variant="secondary" routerLink="/produtos">Cancelar</a>
              <button nxButton type="submit" [loading]="saving()">Salvar produto</button>
            </div>
          }
        </form>
      }
    </div>
  `,
})
export class ProductFormPage implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly fb = inject(FormBuilder);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  protected readonly session = inject(SessionService);

  readonly id = input<string | undefined>(undefined);

  protected readonly units: ProductUnit[] = ['UN', 'CX', 'PCT', 'KG', 'G', 'L', 'ML', 'M'];
  protected readonly unitLabels = UNIT_LABELS;
  protected readonly product = signal<Product | null>(null);
  protected readonly categories = signal<Category[]>([]);
  protected readonly suppliers = signal<Supplier[]>([]);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly loadError = signal<string | null>(null);

  protected readonly isNew = computed(() => !this.id());
  protected readonly costView = computed(() => this.session.hasPermission('PRODUCT_COST_VIEW'));
  protected readonly canSeeSuppliers = computed(
    () => this.session.hasFeature('SUPPLIERS') && this.session.hasPermission('SUPPLIER_READ'),
  );
  protected readonly canEdit = computed(() =>
    this.session.hasPermission(this.isNew() ? 'PRODUCT_CREATE' : 'PRODUCT_UPDATE'),
  );

  protected readonly form = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(150)]],
    sku: [''],
    barcode: [''],
    categoryId: [''],
    supplierId: [''],
    salePrice: [null as number | null, [Validators.required, Validators.min(0)]],
    costPrice: [null as number | null, [Validators.min(0)]],
    unit: ['UN' as ProductUnit, Validators.required],
    minimumStock: [0 as number | null, [Validators.min(0)]],
    initialStock: [0 as number | null, [Validators.min(0)]],
    description: [''],
  });

  ngOnInit(): void {
    this.http.get<Category[]>('/api/categories', { params: params({ active: true }) }).subscribe({
      next: (list) => this.categories.set(list),
      error: () => undefined,
    });
    if (this.canSeeSuppliers()) {
      this.http
        .get<Page<Supplier>>('/api/suppliers', { params: params({ active: true, size: 100 }) })
        .subscribe({ next: (page) => this.suppliers.set(page.content), error: () => undefined });
    }
    if (!this.isNew()) this.load();
    if (!this.canEdit()) this.form.disable();
  }

  protected load(): void {
    this.loadError.set(null);
    this.http.get<Product>(`/api/products/${this.id()}`).subscribe({
      next: (product) => {
        this.product.set(product);
        this.form.patchValue({
          name: product.name,
          sku: product.sku ?? '',
          barcode: product.barcode ?? '',
          categoryId: product.categoryId ?? '',
          supplierId: product.supplierId ?? '',
          salePrice: product.salePrice,
          costPrice: product.costPrice ?? null,
          unit: product.unit,
          minimumStock: product.minimumStock,
          description: product.description ?? '',
        });
      },
      error: (error) => this.loadError.set(errorMessage(error)),
    });
  }

  protected save(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.saving()) return;
    const value = this.form.getRawValue();
    const request: ProductRequest = {
      name: value.name ?? '',
      sku: value.sku || null,
      barcode: value.barcode || null,
      categoryId: value.categoryId || null,
      supplierId: this.canSeeSuppliers() ? value.supplierId || null : null,
      salePrice: Number(value.salePrice),
      costPrice: this.costView() && value.costPrice !== null ? Number(value.costPrice) : null,
      unit: value.unit ?? 'UN',
      minimumStock: value.minimumStock ?? 0,
      initialStock: this.isNew() ? value.initialStock ?? 0 : null,
      description: value.description || null,
    };
    this.saving.set(true);
    this.error.set(null);
    const call = this.isNew()
      ? this.http.post<Product>('/api/products', request)
      : this.http.put<Product>(`/api/products/${this.id()}`, request);
    call.subscribe({
      next: () => {
        this.saving.set(false);
        this.toast.success(this.isNew() ? 'Produto cadastrado.' : 'Produto atualizado.');
        void this.router.navigate(['/produtos']);
      },
      error: (error) => {
        this.saving.set(false);
        this.error.set(errorMessage(error));
      },
    });
  }
}
