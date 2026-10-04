import { ChangeDetectionStrategy, Component, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { PeriodPreset } from '../../../core/models/api.models';
import { todayIso } from '../../../core/util/http-params';

export interface PeriodValue {
  preset: PeriodPreset;
  from?: string;
  to?: string;
}

/** Filtro de período: Hoje, 7 dias, 30 dias ou Personalizado. */
@Component({
  selector: 'nx-period-filter',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule],
  template: `
    <div class="nx-segmented" role="group" aria-label="Período">
      @for (option of options; track option.value) {
        <button
          type="button"
          [class.is-active]="value().preset === option.value"
          (click)="select(option.value)"
        >
          {{ option.label }}
        </button>
      }
    </div>
    @if (value().preset === 'CUSTOM') {
      <div class="nx-period__custom">
        <input class="nx-control" type="date" aria-label="Data inicial" [ngModel]="from()" (ngModelChange)="from.set($event)" />
        <span>até</span>
        <input class="nx-control" type="date" aria-label="Data final" [ngModel]="to()" (ngModelChange)="to.set($event)" />
        <button type="button" class="nx-period__apply" (click)="applyCustom()">Aplicar</button>
      </div>
    }
  `,
  styles: `
    :host { display: flex; flex-wrap: wrap; align-items: center; gap: var(--nx-space-3); }
    .nx-period__custom { display: flex; flex-wrap: wrap; align-items: center; gap: var(--nx-space-2); color: var(--nx-text-muted); font-size: var(--nx-text-sm); }
    .nx-period__custom input { width: auto; height: 36px; }
    .nx-period__apply {
      height: 36px;
      padding: 0 var(--nx-space-3);
      border: 1px solid var(--nx-primary-line);
      border-radius: var(--nx-radius-md);
      background: var(--nx-primary-soft);
      color: var(--nx-primary);
      font-weight: 600;
      cursor: pointer;
    }
  `,
})
export class PeriodFilterComponent {
  readonly value = input.required<PeriodValue>();
  readonly valueChange = output<PeriodValue>();

  protected readonly from = signal(todayIso());
  protected readonly to = signal(todayIso());

  protected readonly options: { value: PeriodPreset; label: string }[] = [
    { value: 'TODAY', label: 'Hoje' },
    { value: 'LAST_7_DAYS', label: '7 dias' },
    { value: 'LAST_30_DAYS', label: '30 dias' },
    { value: 'CUSTOM', label: 'Personalizado' },
  ];

  protected select(preset: PeriodPreset): void {
    if (preset === 'CUSTOM') {
      this.valueChange.emit({ preset, from: this.from(), to: this.to() });
    } else {
      this.valueChange.emit({ preset });
    }
  }

  protected applyCustom(): void {
    this.valueChange.emit({ preset: 'CUSTOM', from: this.from(), to: this.to() });
  }
}

/** Converte o filtro em parâmetros da API. */
export function periodParams(value: PeriodValue): Record<string, string | undefined> {
  return value.preset === 'CUSTOM'
    ? { period: 'CUSTOM', from: value.from, to: value.to }
    : { period: value.preset };
}
