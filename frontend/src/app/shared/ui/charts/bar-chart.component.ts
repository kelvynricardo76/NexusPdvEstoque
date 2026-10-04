import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

export interface ChartDatum {
  label: string;
  value: number;
}

/** Gráfico de barras em SVG (sem bibliotecas), responsivo pela largura do container. */
@Component({
  selector: 'nx-bar-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (bars().length) {
      <svg [attr.viewBox]="'0 0 ' + width + ' ' + height()" preserveAspectRatio="none" role="img" [attr.aria-label]="ariaLabel()">
        <defs>
          <linearGradient id="nx-bar-gradient" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stop-color="#BEF264" />
            <stop offset="100%" stop-color="#65A30D" />
          </linearGradient>
        </defs>
        @for (tick of ticks(); track $index) {
          <line [attr.x1]="padLeft" [attr.x2]="width" [attr.y1]="tick.y" [attr.y2]="tick.y" class="grid" />
          <text [attr.x]="padLeft - 6" [attr.y]="tick.y + 3" class="axis" text-anchor="end">{{ tick.label }}</text>
        }
        @for (bar of bars(); track $index) {
          <rect
            [attr.x]="bar.x"
            [attr.y]="bar.y"
            [attr.width]="bar.width"
            [attr.height]="bar.height"
            rx="3"
            fill="url(#nx-bar-gradient)"
          >
            <title>{{ bar.label }}: {{ bar.formatted }}</title>
          </rect>
          @if (showLabels() && bar.showLabel) {
            <text [attr.x]="bar.x + bar.width / 2" [attr.y]="height() - 4" class="axis" text-anchor="middle">{{ bar.label }}</text>
          }
        }
      </svg>
    } @else {
      <p class="empty">Sem dados no período.</p>
    }
  `,
  styles: `
    :host { display: block; width: 100%; }
    svg { display: block; width: 100%; height: auto; overflow: visible; }
    .grid { stroke: var(--nx-border); stroke-width: 1; }
    .axis { fill: var(--nx-text-subtle); font-size: 10px; font-family: var(--nx-font-sans); }
    rect { transition: opacity var(--nx-duration); }
    rect:hover { opacity: 0.8; }
    .empty { padding: var(--nx-space-8) 0; color: var(--nx-text-muted); text-align: center; font-size: var(--nx-text-sm); }
  `,
})
export class BarChartComponent {
  readonly data = input.required<ChartDatum[]>();
  readonly height = input<number>(200);
  readonly money = input<boolean>(true);
  readonly showLabels = input<boolean>(true);
  readonly ariaLabel = input<string>('Gráfico de barras');

  protected readonly width = 600;
  protected readonly padLeft = 48;

  private readonly max = computed(() => niceMax(Math.max(0, ...this.data().map((d) => d.value))));

  protected readonly ticks = computed(() => {
    const chartHeight = this.height() - 24;
    return [0, 0.25, 0.5, 0.75, 1].map((ratio) => ({
      y: 8 + chartHeight * (1 - ratio),
      label: compact(this.max() * ratio, this.money()),
    }));
  });

  protected readonly bars = computed(() => {
    const data = this.data();
    if (!data.length) return [];
    const chartHeight = this.height() - 24;
    const slot = (this.width - this.padLeft) / data.length;
    const barWidth = Math.max(4, Math.min(42, slot * 0.6));
    const labelEvery = Math.max(1, Math.ceil(data.length / 12));
    return data.map((datum, index) => {
      const barHeight = this.max() === 0 ? 0 : (datum.value / this.max()) * chartHeight;
      return {
        label: datum.label,
        formatted: this.money() ? money(datum.value) : datum.value.toLocaleString('pt-BR'),
        x: this.padLeft + slot * index + (slot - barWidth) / 2,
        y: 8 + chartHeight - barHeight,
        width: barWidth,
        height: Math.max(barHeight, datum.value > 0 ? 2 : 0),
        showLabel: index % labelEvery === 0,
      };
    });
  });
}

export function niceMax(value: number): number {
  if (value <= 0) return 1;
  const magnitude = Math.pow(10, Math.floor(Math.log10(value)));
  const normalized = value / magnitude;
  const nice = normalized <= 1 ? 1 : normalized <= 2 ? 2 : normalized <= 5 ? 5 : 10;
  return nice * magnitude;
}

export function compact(value: number, isMoney: boolean): string {
  const formatter = new Intl.NumberFormat('pt-BR', { notation: 'compact', maximumFractionDigits: 1 });
  return (isMoney ? 'R$ ' : '') + formatter.format(value);
}

export function money(value: number): string {
  return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(value);
}
