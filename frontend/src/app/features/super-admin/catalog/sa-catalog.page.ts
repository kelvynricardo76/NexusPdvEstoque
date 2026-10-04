import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { limitText } from '../../../core/i18n/labels';
import { FeatureView, LimitView } from '../../../core/models/super-admin.models';
import { ToastService } from '../../../core/ui/toast.service';
import { errorMessage } from '../../../core/util/api-error';
import { ButtonComponent } from '../../../shared/ui/button/button.component';
import { ModalComponent } from '../../../shared/ui/modal/modal.component';
import { PageHeaderComponent } from '../../../shared/ui/states/states.components';

/** Catálogo de funcionalidades e limites. Os códigos são contrato com o backend; nome/descrição são editáveis. */
@Component({
  selector: 'nx-sa-catalog-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, ButtonComponent, ModalComponent, PageHeaderComponent],
  template: `
    <div class="nx-page">
      <nx-page-header title="Funcionalidades e limites" subtitle="Catálogo usado na composição dos planos" />
      <section class="nx-table-wrap">
        <table class="nx-table nx-table--stack">
          <thead><tr><th>Funcionalidade</th><th>Código</th><th>Planos</th><th></th></tr></thead>
          <tbody>
            @for (feature of features(); track feature.code) {
              <tr>
                <td class="nx-stack-main"><span class="nx-table__main">{{ feature.name }}</span><span class="nx-table__sub">{{ feature.description }}</span></td>
                <td data-label="Código"><code>{{ feature.code }}</code></td>
                <td data-label="Planos">{{ feature.plans.join(', ') || '—' }}</td>
                <td class="nx-table__actions"><button nxButton size="sm" variant="ghost" type="button" (click)="editFeature(feature)">Editar</button></td>
              </tr>
            }
          </tbody>
        </table>
      </section>
      <section class="nx-table-wrap">
        <table class="nx-table nx-table--stack">
          <thead><tr><th>Limite</th><th>Código</th><th class="num">Padrão</th><th></th></tr></thead>
          <tbody>
            @for (limit of limits(); track limit.code) {
              <tr>
                <td class="nx-stack-main"><span class="nx-table__main">{{ limit.name }}</span><span class="nx-table__sub">{{ limit.description }}</span></td>
                <td data-label="Código"><code>{{ limit.code }}</code></td>
                <td class="num" data-label="Padrão">{{ text(limit.defaultValue) }}</td>
                <td class="nx-table__actions"><button nxButton size="sm" variant="ghost" type="button" (click)="editLimit(limit)">Editar</button></td>
              </tr>
            }
          </tbody>
        </table>
      </section>
    </div>

    @if (editing(); as item) {
      <nx-modal [title]="'Editar ' + item.code" size="sm" (closed)="editing.set(null)">
        <label class="nx-label" for="cat-name">Nome</label>
        <input id="cat-name" class="nx-control" [ngModel]="name()" (ngModelChange)="name.set($event)" maxlength="100" />
        <label class="nx-label" for="cat-desc" style="margin-top: 1rem">Descrição</label>
        <input id="cat-desc" class="nx-control" [ngModel]="description()" (ngModelChange)="description.set($event)" maxlength="300" />
        @if (item.kind === 'limit') {
          <label class="nx-label" for="cat-default" style="margin-top: 1rem">Valor padrão (−1 = ilimitado)</label>
          <input id="cat-default" class="nx-control" type="number" min="-1" [ngModel]="defaultValue()" (ngModelChange)="defaultValue.set(+$event)" />
        }
        <ng-container modalFooter>
          <button nxButton variant="secondary" type="button" (click)="editing.set(null)">Cancelar</button>
          <button nxButton type="button" (click)="save(item)">Salvar</button>
        </ng-container>
      </nx-modal>
    }
  `,
})
export class SaCatalogPage implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly toast = inject(ToastService);

  protected readonly features = signal<FeatureView[]>([]);
  protected readonly limits = signal<LimitView[]>([]);
  protected readonly editing = signal<{ kind: 'feature' | 'limit'; code: string } | null>(null);
  protected readonly name = signal('');
  protected readonly description = signal('');
  protected readonly defaultValue = signal(0);

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.http.get<FeatureView[]>('/api/super-admin/features').subscribe({ next: (list) => this.features.set(list), error: () => undefined });
    this.http.get<LimitView[]>('/api/super-admin/limits').subscribe({ next: (list) => this.limits.set(list), error: () => undefined });
  }

  protected text(value: number): string {
    return limitText(value);
  }

  protected editFeature(feature: FeatureView): void {
    this.name.set(feature.name);
    this.description.set(feature.description ?? '');
    this.editing.set({ kind: 'feature', code: feature.code });
  }

  protected editLimit(limit: LimitView): void {
    this.name.set(limit.name);
    this.description.set(limit.description ?? '');
    this.defaultValue.set(limit.defaultValue);
    this.editing.set({ kind: 'limit', code: limit.code });
  }

  protected save(item: { kind: 'feature' | 'limit'; code: string }): void {
    const call = item.kind === 'feature'
      ? this.http.put(`/api/super-admin/features/${item.code}`, { name: this.name(), description: this.description() || null })
      : this.http.put(`/api/super-admin/limits/${item.code}`, { name: this.name(), description: this.description() || null, defaultValue: this.defaultValue() });
    call.subscribe({
      next: () => {
        this.editing.set(null);
        this.toast.success('Catálogo atualizado.');
        this.load();
      },
      error: (error) => this.toast.error(errorMessage(error)),
    });
  }
}
