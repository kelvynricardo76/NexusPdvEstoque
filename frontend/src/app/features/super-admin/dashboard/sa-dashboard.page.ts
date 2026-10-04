import { CurrencyPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { PlatformDashboard } from '../../../core/models/super-admin.models';
import { errorMessage } from '../../../core/util/api-error';
import { ButtonComponent } from '../../../shared/ui/button/button.component';
import { DonutChartComponent, DonutSegment } from '../../../shared/ui/charts/donut-chart.component';
import { LineChartComponent } from '../../../shared/ui/charts/line-chart.component';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { ErrorStateComponent, PageHeaderComponent, SkeletonComponent } from '../../../shared/ui/states/states.components';
import { StatCardComponent } from '../../../shared/ui/stat-card/stat-card.component';

const PLAN_COLORS = ['#A3E635', '#3B82F6', '#F59E0B', '#A78BFA', '#22D3EE', '#F472B6'];

@Component({
  selector: 'nx-sa-dashboard-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CurrencyPipe, RouterLink, ButtonComponent, DonutChartComponent, LineChartComponent, IconComponent, ErrorStateComponent, PageHeaderComponent, SkeletonComponent, StatCardComponent],
  template: `
    <div class="nx-page">
      <nx-page-header title="Visão geral da plataforma" subtitle="Empresas, assinaturas e receita recorrente">
        <a nxButton routerLink="/super-admin/empresas/nova"><nx-icon name="plus" [size]="16" /> Nova empresa</a>
      </nx-page-header>
      @if (error(); as message) {
        <nx-error-state [message]="message" (retry)="load()" />
      } @else if (!data()) {
        <nx-skeleton [rows]="8" />
      } @else {
        @let d = data()!;
        <div class="nx-grid nx-grid--kpi">
          <nx-stat-card icon="building" label="Empresas cadastradas" [value]="d.totalTenants.toString()" [hint]="d.newTenantsLast30Days + ' novas em 30 dias'" />
          <nx-stat-card icon="check-circle" label="Ativas" [value]="d.activeTenants.toString()" />
          <nx-stat-card icon="clock" label="Em trial" [value]="d.trialTenants.toString()" />
          <nx-stat-card icon="alert-triangle" label="Inadimplentes" [value]="d.pastDueTenants.toString()" [hintTone]="d.pastDueTenants ? 'warning' : 'muted'"
                        [hint]="d.suspendedTenants + ' suspensas • ' + d.canceledTenants + ' canceladas'" />
          <nx-stat-card icon="dollar" label="MRR" [value]="(d.monthlyRecurringRevenue | currency) ?? ''" hint="Receita mensal recorrente" />
        </div>
        <div class="nx-grid nx-grid--main-side">
          <section class="nx-panel">
            <h2 class="nx-panel__title">Novos clientes (30 dias)</h2>
            <nx-line-chart [data]="newTenants()" [money]="false" [height]="200" />
          </section>
          <section class="nx-panel">
            <h2 class="nx-panel__title">Distribuição por plano</h2>
            <nx-donut-chart [segments]="plans()" />
          </section>
        </div>
      }
    </div>
  `,
})
export class SaDashboardPage implements OnInit {
  private readonly http = inject(HttpClient);
  protected readonly data = signal<PlatformDashboard | null>(null);
  protected readonly error = signal<string | null>(null);

  protected readonly newTenants = computed(() =>
    (this.data()?.newTenantsByDay ?? []).map((day) => ({ label: day.date.slice(8, 10) + '/' + day.date.slice(5, 7), value: day.count })),
  );
  protected readonly plans = computed<DonutSegment[]>(() =>
    (this.data()?.planDistribution ?? []).map((plan, index) => ({ label: plan.planName, value: plan.tenants, color: PLAN_COLORS[index % PLAN_COLORS.length] })),
  );

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.error.set(null);
    this.http.get<PlatformDashboard>('/api/super-admin/dashboard').subscribe({
      next: (data) => this.data.set(data),
      error: (error) => this.error.set(errorMessage(error)),
    });
  }
}
