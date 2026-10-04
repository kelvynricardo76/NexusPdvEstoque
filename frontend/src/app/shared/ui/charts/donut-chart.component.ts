import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

export interface DonutSegment {
  label: string;
  value: number;
  color: string;
}

/** Gráfico de rosca em SVG com legenda (ex.: formas de pagamento). */
@Component({
  selector: 'nx-donut-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DecimalPipe],
  template: `
    @if (total() > 0) {
      <svg viewBox="0 0 120 120" role="img" aria-label="Distribuição">
        <circle cx="60" cy="60" r="44" fill="none" stroke="var(--nx-surface-3)" stroke-width="18" />
        @for (arc of arcs(); track arc.label) {
          <circle
            cx="60"
            cy="60"
            r="44"
            fill="none"
            [attr.stroke]="arc.color"
            stroke-width="18"
            [attr.stroke-dasharray]="arc.dash + ' ' + circumference"
            [attr.stroke-dashoffset]="-arc.offset"
            transform="rotate(-90 60 60)"
          >
            <title>{{ arc.label }}: {{ arc.percent | number: '1.0-1' }}%</title>
          </circle>
        }
      </svg>
      <ul class="legend">
        @for (arc of arcs(); track arc.label) {
          <li>
            <span class="dot" [style.background]="arc.color"></span>
            <span class="name">{{ arc.label }}</span>
            <span class="pct">{{ arc.percent | number: '1.0-0' }}%</span>
          </li>
        }
      </ul>
    } @else {
      <p class="empty">Sem dados no período.</p>
    }
  `,
  styles: `
    :host { display: flex; flex-wrap: wrap; align-items: center; justify-content: center; gap: var(--nx-space-6); }
    svg { width: 150px; height: 150px; flex-shrink: 0; }
    .legend { display: flex; flex-direction: column; gap: var(--nx-space-2); margin: 0; padding: 0; list-style: none; min-width: 160px; }
    li { display: flex; align-items: center; gap: var(--nx-space-2); font-size: var(--nx-text-sm); }
    .dot { width: 10px; height: 10px; border-radius: 50%; flex-shrink: 0; }
    .name { flex: 1; color: var(--nx-text-muted); }
    .pct { font-weight: 600; font-variant-numeric: tabular-nums; }
    .empty { padding: var(--nx-space-8) 0; color: var(--nx-text-muted); font-size: var(--nx-text-sm); }
  `,
})
export class DonutChartComponent {
  readonly segments = input.required<DonutSegment[]>();

  protected readonly circumference = 2 * Math.PI * 44;
  protected readonly total = computed(() => this.segments().reduce((sum, segment) => sum + segment.value, 0));

  protected readonly arcs = computed(() => {
    let offset = 0;
    return this.segments()
      .filter((segment) => segment.value > 0)
      .map((segment) => {
        const fraction = segment.value / this.total();
        const arc = {
          label: segment.label,
          color: segment.color,
          percent: fraction * 100,
          dash: fraction * this.circumference,
          offset,
        };
        offset += arc.dash;
        return arc;
      });
  });
}
