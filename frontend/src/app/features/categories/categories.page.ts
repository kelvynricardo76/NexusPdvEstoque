import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { SessionService } from '../../core/auth/session.service';
import { Category } from '../../core/models/api.models';
import { ConfirmService } from '../../core/ui/confirm.service';
import { ToastService } from '../../core/ui/toast.service';
import { errorMessage } from '../../core/util/api-error';
import { params } from '../../core/util/http-params';
import { AlertComponent } from '../../shared/ui/alert/alert.component';
import { BadgeComponent } from '../../shared/ui/badge/badge.component';
import { ButtonComponent } from '../../shared/ui/button/button.component';
import { IconComponent } from '../../shared/ui/icon/icon.component';
import { ModalComponent } from '../../shared/ui/modal/modal.component';
import { EmptyStateComponent, ErrorStateComponent, PageHeaderComponent, SkeletonComponent } from '../../shared/ui/states/states.components';

@Component({
  selector: 'nx-categories-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, ReactiveFormsModule, AlertComponent, BadgeComponent, ButtonComponent, IconComponent, ModalComponent, EmptyStateComponent, ErrorStateComponent, PageHeaderComponent, SkeletonComponent],
  template: `
    <div class="nx-page">
      <nx-page-header title="Categorias" subtitle="Organize seus produtos por categoria">
        @if (session.hasPermission('CATEGORY_CREATE')) {
          <button nxButton type="button" (click)="open(null)"><nx-icon name="plus" [size]="16" /> Nova categoria</button>
        }
      </nx-page-header>

      <div class="nx-toolbar">
        <div class="nx-search">
          <nx-icon name="search" [size]="16" />
          <input class="nx-control" type="search" placeholder="Buscar categoria" [ngModel]="query()" (ngModelChange)="query.set($event); load()" />
        </div>
      </div>

      <div class="nx-table-wrap">
        @if (error(); as message) {
          <nx-error-state [message]="message" (retry)="load()" />
        } @else if (loading()) {
          <div style="padding: 1rem"><nx-skeleton [rows]="5" /></div>
        } @else if (categories().length) {
          <table class="nx-table nx-table--stack">
            <thead><tr><th>Categoria</th><th>Descrição</th><th class="num">Produtos</th><th>Status</th><th></th></tr></thead>
            <tbody>
              @for (category of categories(); track category.id) {
                <tr>
                  <td class="nx-stack-main nx-table__main">{{ category.name }}</td>
                  <td data-label="Descrição" class="text-muted">{{ category.description || '—' }}</td>
                  <td class="num" data-label="Produtos">{{ category.productCount }}</td>
                  <td><nx-badge [tone]="category.active ? 'success' : 'neutral'">{{ category.active ? 'Ativa' : 'Inativa' }}</nx-badge></td>
                  <td class="nx-table__actions">
                    @if (session.hasPermission('CATEGORY_UPDATE')) {
                      <button type="button" class="nx-icon-button" (click)="open(category)" aria-label="Editar"><nx-icon name="edit" [size]="16" /></button>
                    }
                    @if (session.hasPermission('CATEGORY_DISABLE')) {
                      <button type="button" class="nx-icon-button" [class.is-danger]="category.active" (click)="toggle(category)"
                              [attr.aria-label]="category.active ? 'Desativar' : 'Ativar'">
                        <nx-icon [name]="category.active ? 'power' : 'check-circle'" [size]="16" />
                      </button>
                    }
                  </td>
                </tr>
              }
            </tbody>
          </table>
        } @else {
          <nx-empty-state icon="folder" title="Nenhuma categoria" message="Crie categorias para organizar os produtos e filtrar o PDV." />
        }
      </div>
    </div>

    @if (editing() !== undefined) {
      <nx-modal [title]="editing() ? 'Editar categoria' : 'Nova categoria'" size="sm" (closed)="editing.set(undefined)">
        <form [formGroup]="form" (ngSubmit)="save()" id="category-form" class="nx-form-grid">
          <div class="nx-form-grid__full">
            <label class="nx-label" for="c-name">Nome *</label>
            <input id="c-name" class="nx-control" formControlName="name" maxlength="80" />
          </div>
          <div class="nx-form-grid__full">
            <label class="nx-label" for="c-desc">Descrição</label>
            <textarea id="c-desc" class="nx-control" rows="2" formControlName="description" maxlength="300"></textarea>
          </div>
          @if (formError(); as message) { <nx-alert class="nx-form-grid__full" tone="danger">{{ message }}</nx-alert> }
        </form>
        <ng-container modalFooter>
          <button nxButton variant="secondary" type="button" (click)="editing.set(undefined)">Cancelar</button>
          <button nxButton type="submit" form="category-form" [loading]="saving()">Salvar</button>
        </ng-container>
      </nx-modal>
    }
  `,
})
export class CategoriesPage implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly fb = inject(FormBuilder);
  private readonly toast = inject(ToastService);
  private readonly confirm = inject(ConfirmService);
  protected readonly session = inject(SessionService);

  protected readonly query = signal('');
  protected readonly categories = signal<Category[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  /** undefined = fechado; null = nova; Category = edição. */
  protected readonly editing = signal<Category | null | undefined>(undefined);
  protected readonly saving = signal(false);
  protected readonly formError = signal<string | null>(null);
  protected readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(80)]],
    description: [''],
  });

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.error.set(null);
    this.http.get<Category[]>('/api/categories', { params: params({ q: this.query().trim() }) }).subscribe({
      next: (list) => {
        this.categories.set(list);
        this.loading.set(false);
      },
      error: (error) => {
        this.error.set(errorMessage(error));
        this.loading.set(false);
      },
    });
  }

  protected open(category: Category | null): void {
    this.formError.set(null);
    this.form.reset({ name: category?.name ?? '', description: category?.description ?? '' });
    this.editing.set(category);
  }

  protected save(): void {
    if (this.form.invalid) {
      this.formError.set('Informe o nome da categoria.');
      return;
    }
    const current = this.editing();
    this.saving.set(true);
    const body = this.form.getRawValue();
    const call = current
      ? this.http.put<Category>(`/api/categories/${current.id}`, body)
      : this.http.post<Category>('/api/categories', body);
    call.subscribe({
      next: () => {
        this.saving.set(false);
        this.editing.set(undefined);
        this.toast.success('Categoria salva.');
        this.load();
      },
      error: (error) => {
        this.saving.set(false);
        this.formError.set(errorMessage(error));
      },
    });
  }

  protected async toggle(category: Category): Promise<void> {
    if (category.active && !(await this.confirm.confirm({
      title: 'Desativar categoria',
      message: `Desativar "${category.name}"? Os produtos continuam cadastrados.`,
      confirmText: 'Desativar',
      tone: 'danger',
    }))) return;
    this.http.put(`/api/categories/${category.id}/active`, { active: !category.active }).subscribe({
      next: () => this.load(),
      error: (error) => this.toast.error(errorMessage(error)),
    });
  }
}
