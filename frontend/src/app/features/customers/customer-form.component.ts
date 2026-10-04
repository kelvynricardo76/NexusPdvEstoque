import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, input, OnInit, output, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { CustomerDetail, CustomerRequest } from '../../core/models/api.models';
import { errorMessage } from '../../core/util/api-error';
import { AlertComponent } from '../../shared/ui/alert/alert.component';
import { ButtonComponent } from '../../shared/ui/button/button.component';
import { ModalComponent } from '../../shared/ui/modal/modal.component';

/** Formulário de cliente em modal (criação e edição). Coleta apenas o necessário (LGPD). */
@Component({
  selector: 'nx-customer-form',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, AlertComponent, ButtonComponent, ModalComponent],
  template: `
    <nx-modal [title]="customer() ? 'Editar cliente' : 'Novo cliente'" (closed)="closed.emit()">
      <form id="customer-form" class="nx-form-grid" [formGroup]="form" (ngSubmit)="save()" novalidate>
        <div class="nx-form-grid__full">
          <label class="nx-label" for="cu-name">Nome *</label>
          <input id="cu-name" class="nx-control" formControlName="name" maxlength="150" />
        </div>
        <div>
          <label class="nx-label" for="cu-doc">CPF/CNPJ</label>
          <input id="cu-doc" class="nx-control" formControlName="document" maxlength="20" inputmode="numeric" />
        </div>
        <div>
          <label class="nx-label" for="cu-phone">Telefone</label>
          <input id="cu-phone" class="nx-control" formControlName="phone" maxlength="30" inputmode="tel" />
        </div>
        <div class="nx-form-grid__full">
          <label class="nx-label" for="cu-email">E-mail</label>
          <input id="cu-email" class="nx-control" type="email" formControlName="email" maxlength="254" />
        </div>
        <div class="nx-form-grid__full">
          <label class="nx-label" for="cu-address">Endereço</label>
          <input id="cu-address" class="nx-control" formControlName="address" maxlength="300" />
        </div>
        <div class="nx-form-grid__full">
          <label class="nx-label" for="cu-notes">Observações</label>
          <textarea id="cu-notes" class="nx-control" rows="2" formControlName="notes" maxlength="1000"></textarea>
          <p class="nx-hint">Registre apenas informações necessárias ao atendimento (LGPD).</p>
        </div>
        @if (error(); as message) { <nx-alert class="nx-form-grid__full" tone="danger">{{ message }}</nx-alert> }
      </form>
      <ng-container modalFooter>
        <button nxButton variant="secondary" type="button" (click)="closed.emit()">Cancelar</button>
        <button nxButton type="submit" form="customer-form" [loading]="saving()">Salvar</button>
      </ng-container>
    </nx-modal>
  `,
})
export class CustomerFormComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly fb = inject(FormBuilder);

  readonly customer = input<CustomerDetail | null>(null);
  readonly closed = output<void>();
  readonly saved = output<CustomerDetail>();

  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(150)]],
    document: [''],
    phone: [''],
    email: ['', [Validators.email]],
    address: [''],
    notes: [''],
  });

  ngOnInit(): void {
    const current = this.customer();
    if (current) {
      this.form.patchValue({
        name: current.name,
        document: current.document ?? '',
        phone: current.phone ?? '',
        email: current.email ?? '',
        address: current.address ?? '',
        notes: current.notes ?? '',
      });
    }
  }

  protected save(): void {
    if (this.form.invalid) {
      this.error.set('Verifique o nome e o e-mail.');
      return;
    }
    const value = this.form.getRawValue();
    const body: CustomerRequest = {
      name: value.name,
      document: value.document || null,
      phone: value.phone || null,
      email: value.email || null,
      address: value.address || null,
      notes: value.notes || null,
    };
    const current = this.customer();
    this.saving.set(true);
    this.error.set(null);
    const call = current
      ? this.http.put<CustomerDetail>(`/api/customers/${current.id}`, body)
      : this.http.post<CustomerDetail>('/api/customers', body);
    call.subscribe({
      next: (customer) => {
        this.saving.set(false);
        this.saved.emit(customer);
      },
      error: (error) => {
        this.saving.set(false);
        this.error.set(errorMessage(error));
      },
    });
  }
}
