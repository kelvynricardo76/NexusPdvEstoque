import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { SessionService } from '../../core/auth/session.service';
import { Page, Supplier } from '../../core/models/api.models';
import { ConfirmService } from '../../core/ui/confirm.service';
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

@Component({
  selector: 'nx-suppliers-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, ReactiveFormsModule, AlertComponent, BadgeComponent, ButtonComponent, IconComponent, ModalComponent, EmptyStateComponent, ErrorStateComponent, PageHeaderComponent, PaginationComponent, SkeletonComponent],
  template: `
    <div class="nx-page">
      <nx-page-header title="Fornecedores" subtitle="Cadastro de fornecedores e parceiros">
        @if (session.hasPermission('SUPPLIER_CREATE')) {
          <button nxButton type="button" (click)="open(null)"><nx-icon name="plus" [size]="16" /> Novo fornecedor</button>
        }
      </nx-page-header>
      <div class="nx-toolbar">
        <div class="nx-search">
          <nx-icon name="search" [size]="16" />
          <input class="nx-control" type="search" placeholder="Razão social, fantasia ou CNPJ" [ngModel]="query()" (ngModelChange)="onQuery($event)" />
        </div>
        <select class="nx-control" [ngModel]="active()" (ngModelChange)="active.set($event); reload()" aria-label="Status">
          <option value="true">Ativos</option><option value="false">Inativos</option><option value="">Todos</option>
        </select>
      </div>
      <div class="nx-table-wrap">
        @if (error(); as message) {
          <nx-error-state [message]="message" (retry)="load()" />
        } @else if (loading() && !page()) {
          <div style="padding: 1rem"><nx-skeleton [rows]="6" /></div>
        } @else if (page(); as p) {
          @if (p.content.length) {
            <table class="nx-table nx-table--stack">
              <thead><tr><th>Fornecedor</th><th>Documento</th><th>Contato</th><th>Status</th><th></th></tr></thead>
              <tbody>
                @for (supplier of p.content; track supplier.id) {
                  <tr>
                    <td class="nx-stack-main">
                      <span class="nx-table__main">{{ supplier.tradeName || supplier.legalName }}</span>
                      @if (supplier.tradeName) { <span class="nx-table__sub">{{ supplier.legalName }}</span> }
                    </td>
                    <td data-label="Documento">{{ supplier.document || '—' }}</td>
                    <td data-label="Contato">{{ supplier.phone || supplier.email || '—' }}</td>
                    <td><nx-badge [tone]="supplier.active ? 'success' : 'neutral'">{{ supplier.active ? 'Ativo' : 'Inativo' }}</nx-badge></td>
                    <td class="nx-table__actions">
                      @if (session.hasPermission('SUPPLIER_UPDATE')) {
                        <button type="button" class="nx-icon-button" (click)="open(supplier)" aria-label="Editar"><nx-icon name="edit" [size]="16" /></button>
                      }
                      @if (session.hasPermission('SUPPLIER_DISABLE')) {
                        <button type="button" class="nx-icon-button" [class.is-danger]="supplier.active" (click)="toggle(supplier)"
                                [attr.aria-label]="supplier.active ? 'Desativar' : 'Ativar'">
                          <nx-icon [name]="supplier.active ? 'power' : 'check-circle'" [size]="16" />
                        </button>
                      }
                    </td>
                  </tr>
                }
              </tbody>
            </table>
            <nx-pagination [page]="p.page" [totalPages]="p.totalPages" [totalElements]="p.totalElements" (pageChange)="goTo($event)" />
          } @else {
            <nx-empty-state icon="truck" title="Nenhum fornecedor cadastrado" />
          }
        }
      </div>
    </div>

    @if (editing() !== undefined) {
      <nx-modal [title]="editing() ? 'Editar fornecedor' : 'Novo fornecedor'" (closed)="editing.set(undefined)">
        <form id="supplier-form" class="nx-form-grid" [formGroup]="form" (ngSubmit)="save()" novalidate>
          <div class="nx-form-grid__full">
            <label class="nx-label" for="su-legal">Razão social *</label>
            <input id="su-legal" class="nx-control" formControlName="legalName" maxlength="150" />
          </div>
          <div>
            <label class="nx-label" for="su-trade">Nome fantasia</label>
            <input id="su-trade" class="nx-control" formControlName="tradeName" maxlength="150" />
          </div>
          <div>
            <label class="nx-label" for="su-doc">CNPJ/CPF</label>
            <input id="su-doc" class="nx-control" formControlName="document" maxlength="20" inputmode="numeric" />
          </div>
          <div>
            <label class="nx-label" for="su-phone">Telefone</label>
            <input id="su-phone" class="nx-control" formControlName="phone" maxlength="30" />
          </div>
          <div>
            <label class="nx-label" for="su-email">E-mail</label>
            <input id="su-email" class="nx-control" type="email" formControlName="email" maxlength="254" />
          </div>
          <div class="nx-form-grid__full">
            <label class="nx-label" for="su-address">Endereço</label>
            <input id="su-address" class="nx-control" formControlName="address" maxlength="300" />
          </div>
          <div class="nx-form-grid__full">
            <label class="nx-label" for="su-notes">Observações</label>
            <textarea id="su-notes" class="nx-control" rows="2" formControlName="notes" maxlength="1000"></textarea>
          </div>
          @if (formError(); as message) { <nx-alert class="nx-form-grid__full" tone="danger">{{ message }}</nx-alert> }
        </form>
        <ng-container modalFooter>
          <button nxButton variant="secondary" type="button" (click)="editing.set(undefined)">Cancelar</button>
          <button nxButton type="submit" form="supplier-form" [loading]="saving()">Salvar</button>
        </ng-container>
      </nx-modal>
    }
  `,
})
export class SuppliersPage implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly fb = inject(FormBuilder);
  private readonly toast = inject(ToastService);
  private readonly confirm = inject(ConfirmService);
  protected readonly session = inject(SessionService);

  protected readonly query = signal('');
  protected readonly active = signal('true');
  protected readonly pageIndex = signal(0);
  protected readonly page = signal<Page<Supplier> | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly editing = signal<Supplier | null | undefined>(undefined);
  protected readonly saving = signal(false);
  protected readonly formError = signal<string | null>(null);
  protected readonly form = this.fb.nonNullable.group({
    legalName: ['', [Validators.required, Validators.maxLength(150)]],
    tradeName: [''],
    document: [''],
    phone: [''],
    email: ['', [Validators.email]],
    address: [''],
    notes: [''],
  });
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
      .get<Page<Supplier>>('/api/suppliers', {
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

  protected open(supplier: Supplier | null): void {
    this.formError.set(null);
    this.form.reset({
      legalName: supplier?.legalName ?? '',
      tradeName: supplier?.tradeName ?? '',
      document: supplier?.document ?? '',
      phone: supplier?.phone ?? '',
      email: supplier?.email ?? '',
      address: supplier?.address ?? '',
      notes: supplier?.notes ?? '',
    });
    this.editing.set(supplier);
  }

  protected save(): void {
    if (this.form.invalid) {
      this.formError.set('Verifique a razão social e o e-mail.');
      return;
    }
    const value = this.form.getRawValue();
    const body = Object.fromEntries(Object.entries(value).map(([key, v]) => [key, v || null]));
    const current = this.editing();
    this.saving.set(true);
    const call = current
      ? this.http.put<Supplier>(`/api/suppliers/${current.id}`, body)
      : this.http.post<Supplier>('/api/suppliers', body);
    call.subscribe({
      next: () => {
        this.saving.set(false);
        this.editing.set(undefined);
        this.toast.success('Fornecedor salvo.');
        this.load();
      },
      error: (error) => {
        this.saving.set(false);
        this.formError.set(errorMessage(error));
      },
    });
  }

  protected async toggle(supplier: Supplier): Promise<void> {
    if (supplier.active && !(await this.confirm.confirm({
      title: 'Desativar fornecedor',
      message: `Desativar "${supplier.tradeName || supplier.legalName}"?`,
      confirmText: 'Desativar',
      tone: 'danger',
    }))) return;
    this.http.put(`/api/suppliers/${supplier.id}/active`, { active: !supplier.active }).subscribe({
      next: () => this.load(),
      error: (error) => this.toast.error(errorMessage(error)),
    });
  }
}
