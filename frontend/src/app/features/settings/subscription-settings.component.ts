import { DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FEATURE_LABELS, label, limitText, TENANT_STATUS } from '../../core/i18n/labels';
import { TenantSubscriptionView } from '../../core/models/api.models';
import { errorMessage } from '../../core/util/api-error';
import { BadgeComponent } from '../../shared/ui/badge/badge.component';
import { ButtonComponent } from '../../shared/ui/button/button.component';
import { IconComponent } from '../../shared/ui/icon/icon.component';
import { ErrorStateComponent, SkeletonComponent } from '../../shared/ui/states/states.components';

/** Assinatura do tenant: plano, situação e consumo dos limites (somente TENANT_ADMIN). */
@Component({
  selector: 'nx-subscription-settings',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, RouterLink, BadgeComponent, ButtonComponent, IconComponent, ErrorStateComponent, SkeletonComponent],
  template: `
    @if (error(); as message) {
      <nx-error-state [message]="message" (retry)="load()" />
    } @else if (!data()) {
      <nx-skeleton [rows]="6" />
    } @else {
      @let s = data()!;
      <div class="nx-grid nx-grid--main-side">
        <section class="nx-panel">
          <div class="nx-panel__header">
            <div>
              <p class="text-muted text-small">Plano atual</p>
              <h2 class="plan">{{ s.planName }}</h2>
            </div>
            <nx-badge [tone]="status(s.status).tone">{{ status(s.status).label }}</nx-badge>
          </div>
          <dl class="nx-definition">
            <dt>Ciclo</dt><dd>{{ s.billingCycle === 'ANNUAL' ? 'Anual' : 'Mensal' }}</dd>
            @if (s.trialEndDate) { <dt>Fim do teste</dt><dd>{{ s.trialEndDate | date: 'dd/MM/yyyy' }}</dd> }
            @if (s.currentPeriodEnd) { <dt>Próxima renovação</dt><dd>{{ s.currentPeriodEnd | date: 'dd/MM/yyyy' }}</dd> }
          </dl>
          <p class="text-muted text-small">Consumo do plano</p>
          @for (usage of usages(s); track usage.label) {
            <div class="usage">
              <div class="usage__line"><span>{{ usage.label }}</span><span class="num">{{ usage.used }} / {{ usage.maxText }}</span></div>
              @if (usage.max > 0) {
                <div class="nx-progress" [class.is-warning]="usage.ratio >= 0.8" [class.is-danger]="usage.ratio >= 1">
                  <span [style.width.%]="usage.ratio * 100"></span>
                </div>
              }
            </div>
          }
          <div class="nx-actions"><a nxButton routerLink="/upgrade"><nx-icon name="sparkles" [size]="16" /> Conhecer planos</a></div>
        </section>
        <section class="nx-panel">
          <h2 class="nx-panel__title">Funcionalidades contratadas</h2>
          <ul class="features">
            @for (feature of s.features; track feature) {
              <li><nx-icon name="check" [size]="14" /> {{ featureLabel(feature) }}</li>
            }
          </ul>
        </section>
      </div>
    }
  `,
  styles: `
    .plan { font-size: var(--nx-text-2xl); color: var(--nx-primary); }
    .usage { display: flex; flex-direction: column; gap: 6px; }
    .usage__line { display: flex; justify-content: space-between; font-size: var(--nx-text-sm); }
    .features { display: grid; gap: var(--nx-space-2); margin: 0; padding: 0; list-style: none; font-size: var(--nx-text-sm); }
    .features li { display: flex; align-items: center; gap: var(--nx-space-2); }
    .features nx-icon { color: var(--nx-primary); }
  `,
})
export class SubscriptionSettingsComponent implements OnInit {
  private readonly http = inject(HttpClient);
  protected readonly data = signal<TenantSubscriptionView | null>(null);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.error.set(null);
    this.http.get<TenantSubscriptionView>('/api/subscription').subscribe({
      next: (data) => this.data.set(data),
      error: (error) => this.error.set(errorMessage(error)),
    });
  }

  protected status(code: string) {
    return TENANT_STATUS[code] ?? { label: code, tone: 'neutral' as const };
  }

  protected featureLabel(code: string): string {
    return label(FEATURE_LABELS, code);
  }

  protected usages(s: TenantSubscriptionView) {
    const entry = (labelText: string, used: number, max: number) => ({
      label: labelText,
      used,
      max,
      maxText: limitText(max),
      ratio: max > 0 ? Math.min(1, used / max) : 0,
    });
    return [
      entry('Usuários ativos', s.usage.activeUsers, s.usage.maxUsers),
      entry('Produtos', s.usage.products, s.usage.maxProducts),
      entry('Vendas no mês', s.usage.salesThisMonth, s.usage.maxMonthlySales),
    ];
  }
}
