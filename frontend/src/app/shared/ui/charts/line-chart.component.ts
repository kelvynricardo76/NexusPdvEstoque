import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { ChartDatum, compact, money, niceMax } from './bar-chart.component';

/** Gráfico de linha com área em SVG (tendência no período). */
@Component({
  selector: 'nx-line-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (points().length > 1) {
      <svg [attr.viewBox]="'0 0 ' + width + ' ' + height()" preserveAspectRatio="none" role="img" aria-label="Tendência">
        <defs>
          <linearGradient id="nx-area-gradient" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stop-color="rgba(163,230,53,0.35)" />
            <stop offset="100%" stop-color="rgba(163,230,53,0)" />
          </linearGradient>
        </defs>
        @for (tick of ticks(); track $index) {
          <line [attr.x1]="padLeft" [attr.x2]="width" [attr.y1]="tick.y" [attr.y2]="tick.y" class="grid" />
          <text [attr.x]="padLeft - 6" [attr.y]="tick.y + 3" class="axis" text-anchor="end">{{ tick.label }}</text>
        }
        <path [attr.d]="area()" fill="url(#nx-area-gradient)" />
        <path [attr.d]="line()" fill="none" stroke="#A3E635" stroke-width="2.2" stroke-linejoin="round" />
        @for (point of points(); track $index) {
          <circle [attr.cx]="point.x" [attr.cy]="point.y" r="3" class="dot">
            <title>{{ point.label }}: {{ point.formatted }}</title>
          </circle>
          @if (point.showLabel) {
            <text [attr.x]="point.x" [attr.y]="height() - 4" class="axis" text-anchor="middle">{{ point.label }}</text>
          }
        }
      </svg>
    } @else {
      <p class="empty">Sem dados suficientes no período.</p>
    }
  `,
  styles: `
    :host { display: block; width: 100%; }
    svg { display: block; width: 100%; height: auto; overflow: visible; }
    .grid { stroke: var(--nx-border); }
    .axis { fill: var(--nx-text-subtle); font-size: 10px; font-family: var(--nx-font-sans); }
    .dot { fill: var(--nx-bg); stroke: #A3E635; stroke-width: 1.6; }
    .empty { padding: var(--nx-space-8) 0; color: var(--nx-text-muted); text-align: center; font-size: var(--nx-text-sm); }
  `,
})
export class LineChartComponent {
  readonly data = input.required<ChartDatum[]>();
  readonly height = input<number>(200);
  readonly money = input<boolean>(true);

  protected readonly width = 600;
  protected readonly padLeft = 48;
  private readonly max = computed(() => niceMax(Math.max(0, ...this.data().map((d) => d.value))));

  protected readonly ticks = computed(() => {
    const chartHeight = this.height() - 24;
    return [0, 0.5, 1].map((ratio) => ({ y: 8 + chartHeight * (1 - ratio), label: compact(this.max() * ratio, this.money()) }));
  });

  protected readonly points = computed(() => {
    const data = this.data();
    const chartHeight = this.height() - 24;
    const step = data.length > 1 ? (this.width - this.padLeft - 8) / (data.length - 1) : 0;
    const labelEvery = Math.max(1, Math.ceil(data.length / 8));
    return data.map((datum, index) => ({
      label: datum.label,
      formatted: this.money() ? money(datum.value) : datum.value.toLocaleString('pt-BR'),
      x: this.padLeft + step * index,
      y: 8 + chartHeight - (datum.value / this.max()) * chartHeight,
      showLabel: index % labelEvery === 0 || index === data.length - 1,
    }));
  });

  protected readonly line = computed(() =>
    this.points().map((point, index) => `${index === 0 ? 'M' : 'L'}${point.x},${point.y}`).join(' '),
  );

  protected readonly area = computed(() => {
    const points = this.points();
    if (!points.length) return '';
    const base = this.height() - 16;
    return `${this.line()} L${points[points.length - 1].x},${base} L${points[0].x},${base} Z`;
  });
}
