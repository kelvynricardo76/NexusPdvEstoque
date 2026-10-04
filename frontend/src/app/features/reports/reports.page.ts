import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { SessionService } from '../../core/auth/session.service';
import { ReportInfo, ReportResult, ReportValueType } from '../../core/models/api.models';
import { ToastService } from '../../core/ui/toast.service';
import { errorMessage } from '../../core/util/api-error';
import { downloadBlob, fileNameFrom, params } from '../../core/util/http-params';
import { AlertComponent } from '../../shared/ui/alert/alert.component';
import { ButtonComponent } from '../../shared/ui/button/button.component';
import { BarChartComponent } from '../../shared/ui/charts/bar-chart.component';
import { IconComponent } from '../../shared/ui/icon/icon.component';
import { IconName } from '../../shared/ui/icon/icons';
import { PeriodFilterComponent, periodParams, PeriodValue } from '../../shared/ui/period-filter/period-filter.component';
import { EmptyStateComponent, PageHeaderComponent, SkeletonComponent } from '../../shared/ui/states/states.components';

const REPORT_ICONS: Record<string, IconName> = {
  SALES: 'receipt',
  STOCK: 'package',
  LOW_STOCK: 'alert-triangle',
  TOP_PRODUCTS: 'trending-up',
  MOVEMENTS: 'repeat',
  CUSTOMERS: 'users',
  FINANCIAL: 'wallet',
};

@Component({
  selector: 'nx-reports-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, AlertComponent, ButtonComponent, BarChartComponent, IconComponent, PeriodFilterComponent, EmptyStateComponent, PageHeaderComponent, SkeletonComponent],
  templateUrl: './reports.page.html',
  styleUrl: './reports.page.scss',
})
export class ReportsPage implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly toast = inject(ToastService);
  protected readonly session = inject(SessionService);

  protected readonly catalog = signal<ReportInfo[]>([]);
  protected readonly selected = signal<ReportInfo | null>(null);
  protected readonly period = signal<PeriodValue>({ preset: 'LAST_30_DAYS' });
  protected readonly result = signal<ReportResult | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly exporting = signal<string | null>(null);

  /** Relatórios visíveis: permitidos + (para o admin) os não contratados com cadeado. */
  protected readonly visible = computed(() =>
    this.catalog().filter((report) => report.allowed || (!report.availableInPlan && this.session.isTenantAdmin())),
  );

  protected readonly canExport = computed(() => this.session.hasPermission('REPORT_EXPORT'));
  protected readonly csv = computed(() => this.canExport() && this.session.hasFeature('CSV_EXPORT'));
  protected readonly pdf = computed(() => this.canExport() && this.session.hasFeature('PDF_EXPORT'));
  protected readonly exportLocked = computed(
    () => this.session.isTenantAdmin() && !this.session.hasFeature('CSV_EXPORT') && !this.session.hasFeature('PDF_EXPORT'),
  );
  protected readonly isSnapshot = computed(() => ['STOCK', 'LOW_STOCK'].includes(this.selected()?.type ?? ''));
  protected readonly chartData = computed(() =>
    (this.result()?.chart ?? []).map((point) => ({ label: point.label, value: Number(point.value) })),
  );
  protected readonly chartIsMoney = computed(() => !['TOP_PRODUCTS'].includes(this.result()?.type ?? ''));

  ngOnInit(): void {
    this.http.get<ReportInfo[]>('/api/reports').subscribe({
      next: (catalog) => {
        this.catalog.set(catalog);
        const first = catalog.find((report) => report.allowed);
        if (first) this.select(first);
      },
      error: (error) => this.error.set(errorMessage(error)),
    });
  }

  protected icon(type: string): IconName {
    return REPORT_ICONS[type] ?? 'file-text';
  }

  protected select(report: ReportInfo): void {
    if (!report.allowed) return;
    this.selected.set(report);
    this.generate();
  }

  protected changePeriod(value: PeriodValue): void {
    this.period.set(value);
    this.generate();
  }

  protected generate(): void {
    const report = this.selected();
    if (!report) return;
    this.loading.set(true);
    this.error.set(null);
    this.http.get<ReportResult>(`/api/reports/${report.type}`, { params: params(periodParams(this.period())) }).subscribe({
      next: (result) => {
        this.result.set(result);
        this.loading.set(false);
      },
      error: (error) => {
        this.result.set(null);
        this.error.set(errorMessage(error));
        this.loading.set(false);
      },
    });
  }

  protected export(format: 'CSV' | 'PDF'): void {
    const report = this.selected();
    if (!report) return;
    this.exporting.set(format);
    this.http
      .get(`/api/reports/${report.type}/export`, {
        params: params({ ...periodParams(this.period()), format }),
        responseType: 'blob',
        observe: 'response',
      })
      .subscribe({
        next: (response) => {
          this.exporting.set(null);
          const fallback = `relatorio.${format.toLowerCase()}`;
          downloadBlob(response.body as Blob, fileNameFrom(response.headers.get('Content-Disposition'), fallback));
        },
        error: () => {
          this.exporting.set(null);
          this.toast.error('Não foi possível exportar o relatório.');
        },
      });
  }

  protected format(value: unknown, type: ReportValueType): string {
    if (value === null || value === undefined || value === '') return '—';
    switch (type) {
      case 'MONEY':
        return Number(value).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
      case 'NUMBER':
        return Number(value).toLocaleString('pt-BR', { maximumFractionDigits: 3 });
      case 'INTEGER':
        return Number(value).toLocaleString('pt-BR', { maximumFractionDigits: 0 });
      case 'PERCENT':
        return Number(value).toLocaleString('pt-BR', { maximumFractionDigits: 1 }) + '%';
      case 'DATE':
        return new Date(String(value) + 'T12:00:00').toLocaleDateString('pt-BR');
      case 'DATETIME':
        return new Date(String(value)).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' });
      default:
        return String(value);
    }
  }

  protected isNumeric(type: ReportValueType): boolean {
    return type === 'MONEY' || type === 'NUMBER' || type === 'INTEGER' || type === 'PERCENT';
  }
}
