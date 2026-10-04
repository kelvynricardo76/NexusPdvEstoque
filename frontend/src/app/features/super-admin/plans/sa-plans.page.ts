import { CurrencyPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FEATURE_LABELS, label, limitText } from '../../../core/i18n/labels';
import { PlanView } from '../../../core/models/super-admin.models';
import { errorMessage } from '../../../core/util/api-error';
import { BadgeComponent } from '../../../shared/ui/badge/badge.component';
import { ButtonComponent } from '../../../shared/ui/button/button.component';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { ErrorStateComponent, PageHeaderComponent, SkeletonComponent } from '../../../shared/ui/states/states.components';

@Component({
  selector: 'nx-sa-plans-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CurrencyPipe, RouterLink, BadgeComponent, ButtonComponent, IconComponent, ErrorStateComponent, PageHeaderComponent, SkeletonComponent],
  template: `
    <div class="nx-page">
      <nx-page-header title="Planos" subtitle="Pacotes comerciais de funcionalidades e limites (configuráveis, sem código)">
        <a nxButton routerLink="/super-admin/planos/novo"><nx-icon name="plus" [size]="16" /> Novo plano</a>
      </nx-page-header>
      @if (error(); as message) {
        <nx-error-state [message]="message" (retry)="load()" />
      } @else if (!plans().length) {
        <nx-skeleton [rows]="6" />
      } @else {
        <div class="nx-grid nx-grid--3">
          @for (plan of plans(); track plan.id) {
            <article class="nx-panel">
              <div class="nx-panel__header">
                <div><h2 class="nx-panel__title">{{ plan.name }}</h2><span class="text-muted text-small">{{ plan.code }}</span></div>
                <nx-badge [tone]="plan.active ? 'success' : 'neutral'">{{ plan.active ? 'Ativo' : 'Inativo' }}</nx-badge>
              </div>
              <p class="text-small"><strong class="text-primary">{{ plan.monthlyPrice | currency }}</strong>/mês • {{ plan.annualPrice | currency }}/ano</p>
              <p class="text-muted text-small">{{ plan.description }}</p>
              <p class="text-small">{{ plan.tenantCount }} empresa(s) • {{ plan.features.length }} funcionalidades</p>
              <p class="text-muted text-small">Usuários: {{ limit(plan.limits['MAX_USERS']) }} • Produtos: {{ limit(plan.limits['MAX_PRODUCTS']) }}</p>
              <p class="features text-small">{{ featureList(plan) }}</p>
              <div class="nx-actions"><a nxButton size="sm" variant="secondary" [routerLink]="['/super-admin/planos', plan.id]">Editar</a></div>
            </article>
          }
        </div>
      }
    </div>
  `,
  styles: `.features { color: var(--nx-text-muted); line-height: 1.6; }`,
})
export class SaPlansPage implements OnInit {
  private readonly http = inject(HttpClient);
  protected readonly plans = signal<PlanView[]>([]);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.error.set(null);
    this.http.get<PlanView[]>('/api/super-admin/plans').subscribe({
      next: (plans) => this.plans.set(plans),
      error: (error) => this.error.set(errorMessage(error)),
    });
  }

  protected limit(value: number | undefined): string {
    return limitText(value);
  }

  protected featureList(plan: PlanView): string {
    return plan.features.map((code) => label(FEATURE_LABELS, code)).join(' • ');
  }
}
