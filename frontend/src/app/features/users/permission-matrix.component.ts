import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { PermissionInfo } from '../../core/models/api.models';
import { IconComponent } from '../../shared/ui/icon/icon.component';

interface MatrixRow {
  module: string;
  label: string;
  cells: Record<'VIEW' | 'CREATE' | 'UPDATE' | 'DELETE', PermissionInfo | undefined>;
}

/**
 * Matriz de permissões amigável: VER / CRIAR / EDITAR / EXCLUIR por módulo, mais as
 * operações específicas. Permissões de módulos não contratados aparecem bloqueadas.
 */
@Component({
  selector: 'nx-permission-matrix',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <div class="nx-table-wrap">
      <div class="nx-table-scroll">
        <table class="nx-table matrix">
          <thead>
            <tr><th>Módulo</th>@for (column of columns; track column.key) { <th class="center">{{ column.label }}</th> }</tr>
          </thead>
          <tbody>
            @for (row of rows(); track row.module) {
              <tr>
                <td class="nx-table__main">{{ row.label }}</td>
                @for (column of columns; track column.key) {
                  <td class="center">
                    @if (row.cells[column.key]; as permission) {
                      <label class="cell" [class.is-locked]="!permission.available"
                             [title]="permission.available ? permission.label : 'Módulo não contratado no plano'">
                        @if (permission.available) {
                          <input type="checkbox" [checked]="selected().has(permission.code)" [disabled]="readonly()"
                                 (change)="toggle(permission.code, $any($event.target).checked)" [attr.aria-label]="permission.label" />
                        } @else {
                          <nx-icon name="lock" [size]="14" />
                        }
                      </label>
                    }
                  </td>
                }
              </tr>
            }
          </tbody>
        </table>
      </div>
    </div>

    <p class="special-title">Operações específicas</p>
    <div class="special">
      @for (permission of specials(); track permission.code) {
        <label class="nx-check" [class.is-locked]="!permission.available"
               [title]="permission.available ? '' : 'Módulo não contratado no plano'">
          @if (permission.available) {
            <input type="checkbox" [checked]="selected().has(permission.code)" [disabled]="readonly()"
                   (change)="toggle(permission.code, $any($event.target).checked)" />
          } @else {
            <nx-icon name="lock" [size]="14" />
          }
          <span>{{ permission.label }} <small class="text-muted">({{ permission.moduleLabel }})</small></span>
        </label>
      }
    </div>
  `,
  styles: `
    .matrix .center { text-align: center; }
    .cell { display: inline-flex; padding: 4px; cursor: pointer; }
    .cell input, .special input { width: 18px; height: 18px; accent-color: var(--nx-primary); cursor: pointer; }
    .is-locked { color: var(--nx-text-subtle); cursor: not-allowed; }
    .special-title { margin: var(--nx-space-5) 0 var(--nx-space-2); font-weight: 600; font-size: var(--nx-text-sm); }
    .special { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: var(--nx-space-2) var(--nx-space-4); }
  `,
})
export class PermissionMatrixComponent {
  readonly catalog = input.required<PermissionInfo[]>();
  readonly selected = input.required<Set<string>>();
  readonly readonly = input<boolean>(false);
  readonly selectedChange = output<Set<string>>();

  protected readonly columns = [
    { key: 'VIEW' as const, label: 'Ver' },
    { key: 'CREATE' as const, label: 'Criar' },
    { key: 'UPDATE' as const, label: 'Editar' },
    { key: 'DELETE' as const, label: 'Excluir' },
  ];

  protected readonly rows = computed<MatrixRow[]>(() => {
    const rows = new Map<string, MatrixRow>();
    for (const permission of this.catalog()) {
      if (permission.action === 'SPECIAL') continue;
      const row = rows.get(permission.module) ?? {
        module: permission.module,
        label: permission.moduleLabel,
        cells: { VIEW: undefined, CREATE: undefined, UPDATE: undefined, DELETE: undefined },
      };
      row.cells[permission.action] = permission;
      rows.set(permission.module, row);
    }
    return [...rows.values()];
  });

  protected readonly specials = computed(() => this.catalog().filter((permission) => permission.action === 'SPECIAL'));

  protected toggle(code: string, checked: boolean): void {
    const next = new Set(this.selected());
    if (checked) next.add(code);
    else next.delete(code);
    this.selectedChange.emit(next);
  }
}
