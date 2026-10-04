import { CurrencyPipe, DecimalPipe, LowerCasePipe, SlicePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { SessionService } from '../../core/auth/session.service';
import { label, PAYMENT_METHOD_COLORS, PAYMENT_METHOD_LABELS, STOCK_SITUATION } from '../../core/i18n/labels';
import { Dashboard } from '../../core/models/api.models';
import { errorMessage } from '../../core/util/api-error';
import { params } from '../../core/util/http-params';
import { BadgeComponent } from '../../shared/ui/badge/badge.component';
import { BarChartComponent, ChartDatum, money } from '../../shared/ui/charts/bar-chart.component';
import { DonutChartComponent, DonutSegment } from '../../shared/ui/charts/donut-chart.component';
import { LineChartComponent } from '../../shared/ui/charts/line-chart.component';
import { IconComponent } from '../../shared/ui/icon/icon.component';
import { LockedFeatureComponent } from '../../shared/ui/locked-feature/locked-feature.component';
import { PeriodFilterComponent, periodParams, PeriodValue } from '../../shared/ui/period-filter/period-filter.component';
import { ErrorStateComponent, SkeletonComponent } from '../../shared/ui/states/states.components';
import { StatCardComponent } from '../../shared/ui/stat-card/stat-card.component';

const WEEKDAYS = ['Dom', 'Seg', 'Ter', 'Qua', 'Qui', 'Sex', 'Sáb'];

@Component({
  selector: 'nx-dashboard-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CurrencyPipe,
    DecimalPipe,
    LowerCasePipe,
    SlicePipe,
    RouterLink,
    BadgeComponent,
    BarChartComponent,
    DonutChartComponent,
    LineChartComponent,
    IconComponent,
    LockedFeatureComponent,
    PeriodFilterComponent,
    StatCardComponent,
    SkeletonComponent,
    ErrorStateComponent,
  ],
  templateUrl: './dashboard.page.html',
  styleUrl: './dashboard.page.scss',
})
export class DashboardPage implements OnInit {
  private readonly http = inject(HttpClient);
  protected readonly session = inject(SessionService);

  protected readonly period = signal<PeriodValue>({ preset: 'TODAY' });
  protected readonly data = signal<Dashboard | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly situation = STOCK_SITUATION;

  protected readonly isToday = computed(() => this.period().preset === 'TODAY');
  protected readonly subtitle = computed(() =>
    this.isToday() ? 'Aqui está um resumo do seu negócio hoje.' : 'Aqui está um resumo do seu negócio no período.',
  );

  protected readonly last7 = computed<ChartDatum[]>(() =>
    (this.data()?.last7Days ?? []).map((day) => ({ label: WEEKDAYS[new Date(day.date + 'T12:00:00').getDay()], value: day.total })),
  );

  protected readonly trend = computed<ChartDatum[]>(() =>
    (this.data()?.salesByDay ?? []).map((day) => ({ label: day.date.slice(8, 10) + '/' + day.date.slice(5, 7), value: day.total })),
  );

  protected readonly hourly = computed<ChartDatum[]>(() =>
    (this.data()?.salesByHour ?? []).filter((hour) => hour.hour >= 6 && hour.hour <= 23)
      .map((hour) => ({ label: hour.hour + 'h', value: hour.total })),
  );

  protected readonly payments = computed<DonutSegment[]>(() =>
    (this.data()?.paymentMethods ?? []).map((payment) => ({
      label: label(PAYMENT_METHOD_LABELS, payment.method),
      value: payment.total,
      color: PAYMENT_METHOD_COLORS[payment.method] ?? '#8B958F',
    })),
  );

  protected readonly topMax = computed(() => Math.max(1, ...(this.data()?.topProducts ?? []).map((p) => p.quantity)));

  protected readonly showAdvancedLock = computed(
    () => !this.session.hasFeature('ADVANCED_DASHBOARD') && this.session.isTenantAdmin(),
  );

  ngOnInit(): void {
    this.load();
  }

  protected changePeriod(value: PeriodValue): void {
    this.period.set(value);
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.http.get<Dashboard>('/api/dashboard', { params: params(periodParams(this.period())) }).subscribe({
      next: (data) => {
        this.data.set(data);
        this.loading.set(false);
      },
      error: (error) => {
        this.error.set(errorMessage(error));
        this.loading.set(false);
      },
    });
  }

  protected money(value: number): string {
    return money(value);
  }
}
