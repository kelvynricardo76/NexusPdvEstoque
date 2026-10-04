import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, input, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { FEATURE_LABELS, label, LIMIT_LABELS, limitText, TENANT_STATUS } from '../../../core/i18n/labels';
import { AuditEntry, Page } from '../../../core/models/api.models';
import { PlanView, TenantDetail, TenantUserView } from '../../../core/models/super-admin.models';
import { ConfirmService } from '../../../core/ui/confirm.service';
import { ToastService } from '../../../core/ui/toast.service';
import { errorMessage } from '../../../core/util/api-error';
import { BadgeComponent } from '../../../shared/ui/badge/badge.component';
import { ButtonComponent } from '../../../shared/ui/button/button.component';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { ErrorStateComponent, PageHeaderComponent, SkeletonComponent } from '../../../shared/ui/states/states.components';
import { AuditTableComponent } from '../../audit/audit-table.component';

type Tab = 'info' | 'subscription' | 'features' | 'limits' | 'users' | 'audit';

@Component({
  selector: 'nx-sa-tenant-detail-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CurrencyPipe, DatePipe, FormsModule, RouterLink, BadgeComponent, ButtonComponent, IconComponent, ErrorStateComponent, PageHeaderComponent, SkeletonComponent, AuditTableComponent],
  templateUrl: './sa-tenant-detail.page.html',
})
export class SaTenantDetailPage implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly toast = inject(ToastService);
  private readonly confirm = inject(ConfirmService);
  protected readonly statuses = TENANT_STATUS;

  readonly id = input.required<string>();
  protected readonly tab = signal<Tab>('info');
  protected readonly tenant = signal<TenantDetail | null>(null);
  protected readonly plans = signal<PlanView[]>([]);
  protected readonly users = signal<TenantUserView[]>([]);
  protected readonly audit = signal<AuditEntry[]>([]);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);

  // Edição de informações
  protected readonly info = signal({ name: '', tradeName: '', document: '', email: '', phone: '' });
  // Edição de assinatura
  protected readonly planCode = signal('');
  protected readonly billingCycle = signal<'MONTHLY' | 'ANNUAL'>('MONTHLY');
  protected readonly subStatus = signal('ACTIVE');
  protected readonly trialEnd = signal('');
  protected readonly periodStart = signal('');
  protected readonly periodEnd = signal('');
  protected readonly limitDrafts = signal<Record<string, string>>({});

  ngOnInit(): void {
    this.load();
    this.http.get<PlanView[]>('/api/super-admin/plans').subscribe({ next: (plans) => this.plans.set(plans), error: () => undefined });
  }

  protected load(): void {
    this.error.set(null);
    this.http.get<TenantDetail>(`/api/super-admin/tenants/${this.id()}`).subscribe({
      next: (tenant) => this.apply(tenant),
      error: (error) => this.error.set(errorMessage(error)),
    });
  }

  private apply(tenant: TenantDetail): void {
    this.tenant.set(tenant);
    this.info.set({
      name: tenant.name,
      tradeName: tenant.tradeName ?? '',
      document: tenant.document ?? '',
      email: tenant.email ?? '',
      phone: tenant.phone ?? '',
    });
    const s = tenant.subscription;
    if (s) {
      this.planCode.set(s.planCode);
      this.billingCycle.set(s.billingCycle);
      this.subStatus.set(s.status);
      this.trialEnd.set(s.trialEndDate ?? '');
      this.periodStart.set(s.currentPeriodStart ?? '');
      this.periodEnd.set(s.currentPeriodEnd ?? '');
    }
    const drafts: Record<string, string> = {};
    for (const limit of tenant.entitlements.limits) drafts[limit.code] = limit.override === null || limit.override === undefined ? '' : String(limit.override);
    this.limitDrafts.set(drafts);
  }

  protected selectTab(tab: Tab): void {
    this.tab.set(tab);
    if (tab === 'users') {
      this.http.get<TenantUserView[]>(`/api/super-admin/tenants/${this.id()}/users`).subscribe({ next: (u) => this.users.set(u), error: () => undefined });
    }
    if (tab === 'audit') {
      this.http.get<Page<AuditEntry>>(`/api/super-admin/tenants/${this.id()}/audit?size=50`).subscribe({ next: (p) => this.audit.set(p.content), error: () => undefined });
    }
  }

  private run(call: Observable<TenantDetail>, success: string): void {
    this.busy.set(true);
    call.subscribe({
      next: (tenant) => {
        this.busy.set(false);
        this.apply(tenant);
        this.toast.success(success);
      },
      error: (error) => {
        this.busy.set(false);
        this.toast.error(errorMessage(error));
      },
    });
  }

  protected saveInfo(): void {
    const value = this.info();
    this.run(this.http.put<TenantDetail>(`/api/super-admin/tenants/${this.id()}`, {
      name: value.name,
      tradeName: value.tradeName || null,
      document: value.document || null,
      email: value.email || null,
      phone: value.phone || null,
    }), 'Dados atualizados.');
  }

  protected updateInfo(field: 'name' | 'tradeName' | 'document' | 'email' | 'phone', value: string): void {
    this.info.update((current) => ({ ...current, [field]: value }));
  }

  protected async changeStatus(action: 'SUSPEND' | 'REACTIVATE' | 'CANCEL'): Promise<void> {
    const texts = {
      SUSPEND: { title: 'Suspender empresa', message: 'Os usuários da empresa perderão o acesso operacional imediatamente.', button: 'Suspender', tone: 'danger' as const },
      REACTIVATE: { title: 'Reativar empresa', message: 'A empresa voltará a operar normalmente.', button: 'Reativar', tone: 'primary' as const },
      CANCEL: { title: 'Cancelar empresa', message: 'A empresa e a assinatura serão canceladas. Os dados são preservados.', button: 'Cancelar empresa', tone: 'danger' as const },
    }[action];
    const result = await this.confirm.ask({
      title: texts.title,
      message: texts.message,
      confirmText: texts.button,
      tone: texts.tone,
      requireReason: action !== 'REACTIVATE',
      reasonLabel: 'Motivo',
    });
    if (!result.confirmed) return;
    this.run(this.http.post<TenantDetail>(`/api/super-admin/tenants/${this.id()}/status`, { action, reason: result.reason ?? null }), 'Status atualizado.');
  }

  protected changePlan(): void {
    this.run(this.http.put<TenantDetail>(`/api/super-admin/tenants/${this.id()}/plan`, { planCode: this.planCode(), billingCycle: this.billingCycle() }), 'Plano alterado.');
  }

  protected saveSubscription(): void {
    this.run(this.http.put<TenantDetail>(`/api/super-admin/tenants/${this.id()}/subscription`, {
      status: this.subStatus(),
      trialEndDate: this.trialEnd() || null,
      currentPeriodStart: this.periodStart() || null,
      currentPeriodEnd: this.periodEnd() || null,
      cancelAtPeriodEnd: false,
    }), 'Assinatura atualizada.');
  }

  protected issueInvoice(): void {
    this.busy.set(true);
    this.http.post('/api/super-admin/billing/invoices', { tenantId: this.id() }).subscribe({
      next: () => {
        this.busy.set(false);
        this.toast.success('Fatura emitida. Acompanhe em Cobranças.');
      },
      error: (error) => {
        this.busy.set(false);
        this.toast.error(errorMessage(error));
      },
    });
  }

  protected setFeature(code: string, value: string): void {
    const enabled = value === 'PLAN' ? null : value === 'ON';
    this.run(this.http.put<TenantDetail>(`/api/super-admin/tenants/${this.id()}/feature-overrides`, { featureCode: code, enabled }), 'Funcionalidade atualizada.');
  }

  protected featureChoice(override: boolean | null | undefined): string {
    return override === null || override === undefined ? 'PLAN' : override ? 'ON' : 'OFF';
  }

  protected setLimitDraft(code: string, value: string): void {
    this.limitDrafts.update((drafts) => ({ ...drafts, [code]: value }));
  }

  protected saveLimit(code: string): void {
    const raw = this.limitDrafts()[code]?.trim() ?? '';
    const value = raw === '' ? null : Number(raw);
    if (value !== null && (Number.isNaN(value) || value < -1)) {
      this.toast.error('Informe um número (−1 = ilimitado) ou deixe vazio para seguir o plano.');
      return;
    }
    this.run(this.http.put<TenantDetail>(`/api/super-admin/tenants/${this.id()}/limit-overrides`, { limitCode: code, value }), 'Limite atualizado.');
  }

  protected featureLabel(code: string): string {
    return label(FEATURE_LABELS, code);
  }

  protected limitLabel(code: string, fallback: string): string {
    return LIMIT_LABELS[code] ?? fallback;
  }

  protected limitText(value: number | null | undefined): string {
    return limitText(value);
  }
}
