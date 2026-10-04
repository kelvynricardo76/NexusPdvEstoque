import { CurrencyPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { SessionService } from '../../core/auth/session.service';
import { FEATURE_LABELS, label } from '../../core/i18n/labels';
import { CatalogPlan } from '../../core/models/api.models';
import { AlertComponent } from '../../shared/ui/alert/alert.component';
import { ButtonComponent } from '../../shared/ui/button/button.component';
import { IconComponent } from '../../shared/ui/icon/icon.component';
import { PageHeaderComponent, SkeletonComponent } from '../../shared/ui/states/states.components';

/** Comparativo de planos (UX de upgrade) — preços visíveis apenas para o administrador. */
@Component({
  selector: 'nx-upgrade-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CurrencyPipe, AlertComponent, ButtonComponent, IconComponent, PageHeaderComponent, SkeletonComponent],
  template: `
    <div class="nx-page">
      <nx-page-header title="Planos Nexus" subtitle="Escolha o plano ideal para o momento do seu negócio" />
      @if (requestedFeature(); as feature) {
        <nx-alert tone="info"><strong>{{ featureLabel(feature) }}</strong> não está incluído no seu plano atual ({{ currentPlan() }}).</nx-alert>
      }
      @if (!plans().length) {
        <nx-skeleton [rows]="6" />
      } @else {
        <div class="plans">
          @for (plan of plans(); track plan.code) {
            <article class="plan" [class.is-current]="plan.code === session.me()?.subscription?.planCode"
                     [class.is-highlight]="includesRequested(plan)">
              <header>
                <h2>{{ plan.name }}</h2>
                @if (plan.code === session.me()?.subscription?.planCode) { <span class="tag">Plano atual</span> }
                @else if (includesRequested(plan)) { <span class="tag tag--primary">Inclui o recurso</span> }
              </header>
              <p class="text-muted text-small">{{ plan.description }}</p>
              @if (plan.monthlyPrice !== null && plan.monthlyPrice !== undefined) {
                <p class="price"><strong>{{ plan.monthlyPrice | currency }}</strong><span>/mês</span></p>
              }
              <ul>
                @for (feature of plan.features; track feature.code) {
                  <li [class.is-requested]="feature.code === requestedFeature()"><nx-icon name="check" [size]="14" /> {{ feature.name }}</li>
                }
              </ul>
              @if (plan.code !== session.me()?.subscription?.planCode && session.isTenantAdmin()) {
                <a nxButton [variant]="includesRequested(plan) ? 'primary' : 'secondary'" [block]="true"
                   [href]="'mailto:comercial@nexusdevelopment.com.br?subject=Upgrade para o plano ' + plan.name">
                  Fazer upgrade
                </a>
              }
            </article>
          }
        </div>
      }
    </div>
  `,
  styles: `
    .plans { display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: var(--nx-space-4); }
    .plan { display: flex; flex-direction: column; gap: var(--nx-space-3); padding: var(--nx-space-6); border: 1px solid var(--nx-border); border-radius: var(--nx-radius-xl); background: var(--nx-surface); }
    .plan.is-highlight { border-color: var(--nx-primary); box-shadow: var(--nx-glow); }
    .plan.is-current { border-style: dashed; }
    header { display: flex; align-items: center; justify-content: space-between; gap: var(--nx-space-3); }
    h2 { font-size: var(--nx-text-xl); }
    .tag { padding: 2px 8px; border-radius: 999px; background: var(--nx-surface-3); color: var(--nx-text-muted); font-size: var(--nx-text-xs); font-weight: 600; }
    .tag--primary { background: var(--nx-primary-soft); color: var(--nx-primary); }
    .price strong { font-family: var(--nx-font-display); font-size: 1.8rem; }
    .price span { color: var(--nx-text-muted); }
    ul { display: flex; flex: 1; flex-direction: column; gap: var(--nx-space-2); margin: 0; padding: 0; list-style: none; font-size: var(--nx-text-sm); }
    li { display: flex; align-items: center; gap: var(--nx-space-2); }
    li nx-icon { color: var(--nx-primary); }
    li.is-requested { color: var(--nx-primary); font-weight: 600; }
  `,
})
export class UpgradePage implements OnInit {
  private readonly http = inject(HttpClient);
  protected readonly session = inject(SessionService);
  protected readonly requestedFeature = signal(inject(ActivatedRoute).snapshot.queryParamMap.get('feature'));
  protected readonly plans = signal<CatalogPlan[]>([]);
  protected readonly currentPlan = computed(() => this.session.me()?.subscription?.planName ?? '');

  ngOnInit(): void {
    this.http.get<CatalogPlan[]>('/api/plans/catalog').subscribe({ next: (plans) => this.plans.set(plans), error: () => undefined });
  }

  protected includesRequested(plan: CatalogPlan): boolean {
    const feature = this.requestedFeature();
    return !!feature && plan.features.some((item) => item.code === feature);
  }

  protected featureLabel(code: string): string {
    return label(FEATURE_LABELS, code);
  }
}
