import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, input, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { LIMIT_LABELS } from '../../../core/i18n/labels';
import { FeatureView, LimitView, PlanRequest, PlanView } from '../../../core/models/super-admin.models';
import { ConfirmService } from '../../../core/ui/confirm.service';
import { ToastService } from '../../../core/ui/toast.service';
import { toApiError } from '../../../core/util/api-error';
import { AlertComponent } from '../../../shared/ui/alert/alert.component';
import { ButtonComponent } from '../../../shared/ui/button/button.component';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { PageHeaderComponent } from '../../../shared/ui/states/states.components';

interface LimitDraft {
  unlimited: boolean;
  value: number;
}

/**
 * Editor de planos. Reduzir direitos de um plano em uso (remover feature ou reduzir limite)
 * exige confirmação explícita — nada muda silenciosamente em contratos existentes.
 */
@Component({
  selector: 'nx-sa-plan-editor-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink, AlertComponent, ButtonComponent, IconComponent, PageHeaderComponent],
  template: `
    <div class="nx-page">
      <nx-page-header [title]="isNew() ? 'Novo plano' : 'Editar plano'" [subtitle]="isNew() ? 'Defina preço, funcionalidades e limites' : code() + ' • ' + tenantCount() + ' empresa(s) neste plano'">
        <a nxButton variant="secondary" routerLink="/super-admin/planos"><nx-icon name="arrow-left" [size]="16" /> Planos</a>
      </nx-page-header>

      <div class="nx-grid nx-grid--main-side">
        <section class="nx-panel">
          <h2 class="nx-panel__title">Dados comerciais</h2>
          <div class="nx-form-grid">
            <div><label class="nx-label" for="pl-name">Nome *</label><input id="pl-name" class="nx-control" [ngModel]="name()" (ngModelChange)="name.set($event)" maxlength="80" /></div>
            <div><label class="nx-label" for="pl-code">Código *</label>
              <input id="pl-code" class="nx-control" [ngModel]="code()" (ngModelChange)="code.set(($event || '').toUpperCase())" [disabled]="!isNew()" maxlength="40" placeholder="EX: PREMIUM" /></div>
            <div class="nx-form-grid__full"><label class="nx-label" for="pl-desc">Descrição</label>
              <input id="pl-desc" class="nx-control" [ngModel]="description()" (ngModelChange)="description.set($event)" maxlength="500" /></div>
            <div><label class="nx-label" for="pl-month">Preço mensal (R$)</label><input id="pl-month" class="nx-control" type="number" min="0" step="0.01" [ngModel]="monthly()" (ngModelChange)="monthly.set($event)" /></div>
            <div><label class="nx-label" for="pl-year">Preço anual (R$)</label><input id="pl-year" class="nx-control" type="number" min="0" step="0.01" [ngModel]="annual()" (ngModelChange)="annual.set($event)" /></div>
            <div><label class="nx-label" for="pl-order">Ordem de exibição</label><input id="pl-order" class="nx-control" type="number" min="0" [ngModel]="order()" (ngModelChange)="order.set($event)" /></div>
            <label class="nx-check"><input type="checkbox" [ngModel]="active()" (ngModelChange)="active.set($event)" /> Plano ativo (disponível para novas assinaturas)</label>
          </div>

          <h2 class="nx-panel__title">Funcionalidades</h2>
          <div class="features">
            @for (feature of features(); track feature.code) {
              <label class="nx-check">
                <input type="checkbox" [checked]="selected().has(feature.code)" (change)="toggleFeature(feature.code, $any($event.target).checked)" />
                {{ feature.name }}
              </label>
            }
          </div>
        </section>

        <section class="nx-panel">
          <h2 class="nx-panel__title">Limites</h2>
          @for (limit of limits(); track limit.code) {
            <div class="limit">
              <span class="text-small">{{ limitLabel(limit) }}</span>
              <div class="limit__inputs">
                <input class="nx-control" type="number" min="0" [disabled]="drafts()[limit.code]?.unlimited ?? false"
                       [ngModel]="drafts()[limit.code]?.value" (ngModelChange)="setLimit(limit.code, { value: +$event })" />
                <label class="nx-check"><input type="checkbox" [ngModel]="drafts()[limit.code]?.unlimited" (ngModelChange)="setLimit(limit.code, { unlimited: $event })" /> Ilimitado</label>
              </div>
            </div>
          }
          @if (error(); as message) { <nx-alert tone="danger">{{ message }}</nx-alert> }
          <div class="nx-actions"><button nxButton type="button" [loading]="saving()" (click)="save(false)">Salvar plano</button></div>
        </section>
      </div>
    </div>
  `,
  styles: `
    .features { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: var(--nx-space-2) var(--nx-space-4); }
    .limit { display: flex; flex-direction: column; gap: 6px; }
    .limit__inputs { display: flex; align-items: center; gap: var(--nx-space-3); }
    .limit__inputs .nx-control { width: 140px; }
  `,
})
export class SaPlanEditorPage implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly confirm = inject(ConfirmService);

  readonly id = input<string | undefined>(undefined);
  protected readonly isNew = computed(() => !this.id());

  protected readonly name = signal('');
  protected readonly code = signal('');
  protected readonly description = signal('');
  protected readonly monthly = signal(0);
  protected readonly annual = signal(0);
  protected readonly order = signal(50);
  protected readonly active = signal(true);
  protected readonly tenantCount = signal(0);
  protected readonly features = signal<FeatureView[]>([]);
  protected readonly limits = signal<LimitView[]>([]);
  protected readonly selected = signal<Set<string>>(new Set());
  protected readonly drafts = signal<Record<string, LimitDraft>>({});
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.http.get<FeatureView[]>('/api/super-admin/features').subscribe({ next: (list) => this.features.set(list), error: () => undefined });
    this.http.get<LimitView[]>('/api/super-admin/limits').subscribe({
      next: (list) => {
        this.limits.set(list);
        if (this.isNew()) {
          const drafts: Record<string, LimitDraft> = {};
          for (const limit of list) drafts[limit.code] = { unlimited: limit.defaultValue < 0, value: Math.max(0, limit.defaultValue) };
          this.drafts.set(drafts);
        }
      },
      error: () => undefined,
    });
    if (!this.isNew()) {
      this.http.get<PlanView>(`/api/super-admin/plans/${this.id()}`).subscribe({
        next: (plan) => {
          this.name.set(plan.name);
          this.code.set(plan.code);
          this.description.set(plan.description ?? '');
          this.monthly.set(plan.monthlyPrice);
          this.annual.set(plan.annualPrice);
          this.order.set(plan.displayOrder);
          this.active.set(plan.active);
          this.tenantCount.set(plan.tenantCount);
          this.selected.set(new Set(plan.features));
          const drafts: Record<string, LimitDraft> = {};
          for (const [code, value] of Object.entries(plan.limits)) drafts[code] = { unlimited: value < 0, value: Math.max(0, value) };
          this.drafts.set(drafts);
        },
        error: () => this.error.set('Plano não encontrado.'),
      });
    }
  }

  protected toggleFeature(code: string, checked: boolean): void {
    const next = new Set(this.selected());
    if (checked) next.add(code);
    else next.delete(code);
    this.selected.set(next);
  }

  protected setLimit(code: string, change: Partial<LimitDraft>): void {
    this.drafts.update((drafts) => ({ ...drafts, [code]: { ...(drafts[code] ?? { unlimited: false, value: 0 }), ...change } }));
  }

  protected limitLabel(limit: LimitView): string {
    return LIMIT_LABELS[limit.code] ?? limit.name;
  }

  protected save(confirmImpact: boolean): void {
    if (!this.name().trim() || !/^[A-Z][A-Z0-9_]{1,39}$/.test(this.code())) {
      this.error.set('Informe o nome e um código válido (letras maiúsculas, números e _).');
      return;
    }
    const limits: Record<string, number> = {};
    for (const [code, draft] of Object.entries(this.drafts())) limits[code] = draft.unlimited ? -1 : Math.max(0, Number(draft.value) || 0);
    const body: PlanRequest = {
      code: this.code(),
      name: this.name().trim(),
      description: this.description().trim() || null,
      monthlyPrice: Number(this.monthly()) || 0,
      annualPrice: Number(this.annual()) || 0,
      active: this.active(),
      displayOrder: Number(this.order()) || 0,
      features: [...this.selected()],
      limits,
    };
    this.saving.set(true);
    this.error.set(null);
    const call = this.isNew()
      ? this.http.post<PlanView>('/api/super-admin/plans', body)
      : this.http.put<PlanView>(`/api/super-admin/plans/${this.id()}?confirmImpact=${confirmImpact}`, body);
    call.subscribe({
      next: () => {
        this.saving.set(false);
        this.toast.success('Plano salvo.');
        void this.router.navigate(['/super-admin/planos']);
      },
      error: async (error) => {
        this.saving.set(false);
        const apiError = toApiError(error);
        if (apiError.code === 'CONFIRMATION_REQUIRED') {
          const ok = await this.confirm.confirm({
            title: 'Alteração afeta clientes',
            message: apiError.message,
            confirmText: 'Aplicar mesmo assim',
            tone: 'danger',
          });
          if (ok) this.save(true);
        } else {
          this.error.set(apiError.message);
        }
      },
    });
  }
}
