import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { PlanView, PlatformSettings, TenantDetail } from '../../../core/models/super-admin.models';
import { ToastService } from '../../../core/ui/toast.service';
import { errorMessage } from '../../../core/util/api-error';
import { AlertComponent } from '../../../shared/ui/alert/alert.component';
import { ButtonComponent } from '../../../shared/ui/button/button.component';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { PageHeaderComponent } from '../../../shared/ui/states/states.components';

/** Provisionamento de nova empresa: dados, plano, trial e primeiro administrador. */
@Component({
  selector: 'nx-sa-tenant-create-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, AlertComponent, ButtonComponent, IconComponent, PageHeaderComponent],
  template: `
    <div class="nx-page">
      <nx-page-header title="Nova empresa" subtitle="Cria o tenant, a assinatura, os cargos padrão e o administrador">
        <a nxButton variant="secondary" routerLink="/super-admin/empresas"><nx-icon name="arrow-left" [size]="16" /> Voltar</a>
      </nx-page-header>
      <form class="nx-grid nx-grid--2" [formGroup]="form" (ngSubmit)="save()" novalidate>
        <section class="nx-panel" formGroupName="company">
          <h2 class="nx-panel__title">Empresa</h2>
          <div class="nx-form-grid">
            <div class="nx-form-grid__full"><label class="nx-label" for="t-name">Razão social *</label><input id="t-name" class="nx-control" formControlName="name" maxlength="150" /></div>
            <div><label class="nx-label" for="t-trade">Nome fantasia</label><input id="t-trade" class="nx-control" formControlName="tradeName" maxlength="150" /></div>
            <div><label class="nx-label" for="t-doc">CNPJ/CPF</label><input id="t-doc" class="nx-control" formControlName="document" maxlength="20" /></div>
            <div><label class="nx-label" for="t-email">E-mail</label><input id="t-email" class="nx-control" type="email" formControlName="email" /></div>
            <div><label class="nx-label" for="t-phone">Telefone</label><input id="t-phone" class="nx-control" formControlName="phone" maxlength="30" /></div>
          </div>
          <h2 class="nx-panel__title">Assinatura</h2>
          <div class="nx-form-grid">
            <div>
              <label class="nx-label" for="t-plan">Plano *</label>
              <select id="t-plan" class="nx-control" formControlName="planCode">
                @for (plan of plans(); track plan.code) { <option [value]="plan.code">{{ plan.name }}</option> }
              </select>
            </div>
            <div>
              <label class="nx-label" for="t-cycle">Ciclo</label>
              <select id="t-cycle" class="nx-control" formControlName="billingCycle"><option value="MONTHLY">Mensal</option><option value="ANNUAL">Anual</option></select>
            </div>
            <div>
              <label class="nx-label" for="t-trial">Dias de trial</label>
              <input id="t-trial" class="nx-control" type="number" min="0" max="365" formControlName="trialDays" />
              <p class="nx-hint">0 = assinatura ativa imediatamente.</p>
            </div>
          </div>
        </section>
        <section class="nx-panel" formGroupName="admin">
          <h2 class="nx-panel__title">Administrador da empresa (TENANT_ADMIN)</h2>
          <div class="nx-form-grid">
            <div class="nx-form-grid__full"><label class="nx-label" for="a-name">Nome *</label><input id="a-name" class="nx-control" formControlName="name" maxlength="120" /></div>
            <div class="nx-form-grid__full"><label class="nx-label" for="a-email">E-mail de acesso *</label><input id="a-email" class="nx-control" type="email" formControlName="email" /></div>
            <div><label class="nx-label" for="a-phone">Telefone</label><input id="a-phone" class="nx-control" formControlName="phone" maxlength="30" /></div>
            <div>
              <label class="nx-label" for="a-password">Senha inicial</label>
              <input id="a-password" class="nx-control" type="password" formControlName="password" autocomplete="new-password" />
              <p class="nx-hint">Em branco: o administrador recebe um link para definir a senha.</p>
            </div>
          </div>
          @if (error(); as message) { <nx-alert tone="danger">{{ message }}</nx-alert> }
          <div class="nx-actions"><button nxButton type="submit" [loading]="saving()">Criar empresa</button></div>
        </section>
      </form>
    </div>
  `,
})
export class SaTenantCreatePage implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly fb = inject(FormBuilder);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  protected readonly plans = signal<PlanView[]>([]);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly form = this.fb.nonNullable.group({
    company: this.fb.nonNullable.group({
      name: ['', [Validators.required, Validators.maxLength(150)]],
      tradeName: [''],
      document: [''],
      email: ['', Validators.email],
      phone: [''],
      planCode: ['BASIC', Validators.required],
      billingCycle: ['MONTHLY'],
      trialDays: [14, [Validators.min(0), Validators.max(365)]],
    }),
    admin: this.fb.nonNullable.group({
      name: ['', Validators.required],
      email: ['', [Validators.required, Validators.email]],
      phone: [''],
      password: [''],
    }),
  });

  ngOnInit(): void {
    this.http.get<PlanView[]>('/api/super-admin/plans').subscribe({
      next: (plans) => this.plans.set(plans.filter((plan) => plan.active)),
      error: () => undefined,
    });
    this.http.get<PlatformSettings>('/api/super-admin/settings').subscribe({
      next: (settings) => this.form.controls.company.controls.trialDays.setValue(settings.defaultTrialDays),
      error: () => undefined,
    });
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.error.set('Preencha os campos obrigatórios da empresa e do administrador.');
      return;
    }
    const { company, admin } = this.form.getRawValue();
    const body = {
      name: company.name,
      tradeName: company.tradeName || null,
      document: company.document || null,
      email: company.email || null,
      phone: company.phone || null,
      planCode: company.planCode,
      billingCycle: company.billingCycle,
      trialDays: Number(company.trialDays),
      admin: { name: admin.name, email: admin.email, phone: admin.phone || null, password: admin.password || null },
    };
    this.saving.set(true);
    this.error.set(null);
    this.http.post<TenantDetail>('/api/super-admin/tenants', body).subscribe({
      next: (tenant) => {
        this.saving.set(false);
        this.toast.success('Empresa criada.');
        void this.router.navigate(['/super-admin/empresas', tenant.id]);
      },
      error: (error) => {
        this.saving.set(false);
        this.error.set(errorMessage(error));
      },
    });
  }
}
