import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { PlatformSettings } from '../../../core/models/super-admin.models';
import { ToastService } from '../../../core/ui/toast.service';
import { errorMessage } from '../../../core/util/api-error';
import { ButtonComponent } from '../../../shared/ui/button/button.component';
import { PageHeaderComponent } from '../../../shared/ui/states/states.components';

@Component({
  selector: 'nx-sa-settings-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, ButtonComponent, PageHeaderComponent],
  template: `
    <div class="nx-page">
      <nx-page-header title="Configurações da plataforma" subtitle="Parâmetros globais do SaaS" />
      <section class="nx-panel" style="max-width: 560px">
        <div>
          <label class="nx-label" for="ps-trial">Dias de trial padrão para novas empresas</label>
          <input id="ps-trial" class="nx-control" type="number" min="0" max="365" [ngModel]="trial()" (ngModelChange)="trial.set(+$event)" />
        </div>
        <div>
          <label class="nx-label" for="ps-grace">Carência de inadimplência (dias) antes da suspensão</label>
          <input id="ps-grace" class="nx-control" type="number" min="0" max="90" [ngModel]="grace()" (ngModelChange)="grace.set(+$event)" />
          <p class="nx-hint">Após o vencimento da fatura a assinatura fica inadimplente; passada a carência, é suspensa automaticamente.</p>
        </div>
        <div class="nx-actions"><button nxButton type="button" (click)="save()">Salvar</button></div>
      </section>
    </div>
  `,
})
export class SaSettingsPage implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly toast = inject(ToastService);
  protected readonly trial = signal(14);
  protected readonly grace = signal(7);

  ngOnInit(): void {
    this.http.get<PlatformSettings>('/api/super-admin/settings').subscribe({
      next: (settings) => {
        this.trial.set(settings.defaultTrialDays);
        this.grace.set(settings.pastDueGraceDays);
      },
      error: () => undefined,
    });
  }

  protected save(): void {
    this.http.put('/api/super-admin/settings', { defaultTrialDays: this.trial(), pastDueGraceDays: this.grace() }).subscribe({
      next: () => this.toast.success('Configurações salvas.'),
      error: (error) => this.toast.error(errorMessage(error)),
    });
  }
}
