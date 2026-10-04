import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { SessionService } from '../../core/auth/session.service';
import { TenantSettings } from '../../core/models/api.models';
import { ToastService } from '../../core/ui/toast.service';
import { errorMessage } from '../../core/util/api-error';
import { AlertComponent } from '../../shared/ui/alert/alert.component';
import { ButtonComponent } from '../../shared/ui/button/button.component';
import { IconComponent } from '../../shared/ui/icon/icon.component';
import { SkeletonComponent } from '../../shared/ui/states/states.components';

const TIMEZONES = ['America/Sao_Paulo', 'America/Manaus', 'America/Cuiaba', 'America/Belem', 'America/Fortaleza', 'America/Recife', 'America/Rio_Branco', 'America/Noronha'];

/** Dados e personalização da empresa (white-label limitado: logo e cores do comprovante). */
@Component({
  selector: 'nx-company-settings',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, AlertComponent, ButtonComponent, IconComponent, SkeletonComponent],
  template: `
    @if (!loaded()) {
      <nx-skeleton [rows]="8" />
    } @else {
      <div class="nx-grid nx-grid--main-side">
        <form class="nx-panel" [formGroup]="form" (ngSubmit)="save()" novalidate>
          <h2 class="nx-panel__title">Dados da empresa</h2>
          <div class="nx-form-grid">
            <div class="nx-form-grid__full">
              <label class="nx-label" for="s-company">Razão social *</label>
              <input id="s-company" class="nx-control" formControlName="companyName" maxlength="150" />
            </div>
            <div>
              <label class="nx-label" for="s-trade">Nome fantasia</label>
              <input id="s-trade" class="nx-control" formControlName="tradeName" maxlength="150" />
            </div>
            <div>
              <label class="nx-label" for="s-doc">CNPJ/CPF</label>
              <input id="s-doc" class="nx-control" formControlName="document" maxlength="20" />
            </div>
            <div>
              <label class="nx-label" for="s-phone">Telefone</label>
              <input id="s-phone" class="nx-control" formControlName="phone" maxlength="30" />
            </div>
            <div>
              <label class="nx-label" for="s-email">E-mail</label>
              <input id="s-email" class="nx-control" type="email" formControlName="email" maxlength="254" />
            </div>
            <div class="nx-form-grid__full">
              <label class="nx-label" for="s-address">Endereço</label>
              <input id="s-address" class="nx-control" formControlName="address" maxlength="300" />
            </div>
            <div>
              <label class="nx-label" for="s-tz">Fuso horário</label>
              <select id="s-tz" class="nx-control" formControlName="timezone">
                @for (zone of timezones; track zone) { <option [value]="zone">{{ zone }}</option> }
              </select>
            </div>
            <div>
              <label class="nx-label" for="s-currency">Moeda</label>
              <select id="s-currency" class="nx-control" formControlName="currency"><option value="BRL">Real (BRL)</option></select>
            </div>
            <div>
              <label class="nx-label" for="s-primary">Cor principal (comprovante)</label>
              <input id="s-primary" class="nx-control color" type="color" formControlName="primaryColor" />
            </div>
            <div>
              <label class="nx-label" for="s-secondary">Cor secundária</label>
              <input id="s-secondary" class="nx-control color" type="color" formControlName="secondaryColor" />
            </div>
            <label class="nx-check nx-form-grid__full">
              <input type="checkbox" formControlName="allowNegativeStock" />
              Permitir vender com estoque insuficiente (estoque negativo)
            </label>
          </div>
          @if (error(); as message) { <nx-alert tone="danger">{{ message }}</nx-alert> }
          @if (canEdit) {
            <div class="nx-actions"><button nxButton type="submit" [loading]="saving()">Salvar alterações</button></div>
          } @else {
            <p class="text-muted text-small">Somente usuários com permissão de configurações podem alterar estes dados.</p>
          }
        </form>

        <section class="nx-panel">
          <h2 class="nx-panel__title">Logo</h2>
          <div class="logo-preview">
            @if (settings()?.logoDataUrl) { <img [src]="settings()!.logoDataUrl" alt="Logo da empresa" /> }
            @else { <nx-icon name="store" [size]="40" /> }
          </div>
          <p class="text-muted text-small">PNG, JPEG ou WEBP até 256 KB. Exibida no cabeçalho e no comprovante.</p>
          @if (canEdit) {
            <div class="nx-actions">
              <label class="upload">
                <nx-icon name="upload" [size]="14" /> Enviar logo
                <input type="file" accept="image/png,image/jpeg,image/webp" (change)="uploadLogo($event)" />
              </label>
              @if (settings()?.logoDataUrl) {
                <button nxButton variant="ghost" size="sm" type="button" (click)="removeLogo()">Remover</button>
              }
            </div>
          }
        </section>
      </div>
    }
  `,
  styles: `
    .color { padding: 4px; height: 40px; }
    .logo-preview { display: flex; align-items: center; justify-content: center; height: 140px; border: 1px dashed var(--nx-border-strong); border-radius: var(--nx-radius-lg); color: var(--nx-text-subtle); }
    .logo-preview img { max-width: 80%; max-height: 110px; object-fit: contain; }
    .upload { position: relative; overflow: hidden; display: inline-flex; align-items: center; gap: 6px; height: 34px; padding: 0 12px; border: 1px solid var(--nx-border-strong); border-radius: var(--nx-radius-md); background: var(--nx-surface-2); font-size: var(--nx-text-xs); font-weight: 600; cursor: pointer; }
    .upload input { position: absolute; inset: 0; opacity: 0; cursor: pointer; }
  `,
})
export class CompanySettingsComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly fb = inject(FormBuilder);
  private readonly toast = inject(ToastService);
  private readonly session = inject(SessionService);

  protected readonly canEdit = this.session.hasPermission('SETTINGS_MANAGE');
  protected readonly timezones = TIMEZONES;
  protected readonly settings = signal<TenantSettings | null>(null);
  protected readonly loaded = signal(false);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly form = this.fb.nonNullable.group({
    companyName: ['', [Validators.required, Validators.maxLength(150)]],
    tradeName: [''],
    document: [''],
    phone: [''],
    email: ['', Validators.email],
    address: [''],
    timezone: ['America/Sao_Paulo'],
    currency: ['BRL'],
    primaryColor: ['#A3E635'],
    secondaryColor: ['#0A0F0D'],
    allowNegativeStock: [false],
  });

  ngOnInit(): void {
    this.http.get<TenantSettings>('/api/settings').subscribe({
      next: (settings) => {
        this.apply(settings);
        this.loaded.set(true);
      },
      error: (error) => {
        this.error.set(errorMessage(error));
        this.loaded.set(true);
      },
    });
    if (!this.canEdit) this.form.disable();
  }

  private apply(settings: TenantSettings): void {
    this.settings.set(settings);
    this.form.patchValue({
      companyName: settings.companyName,
      tradeName: settings.tradeName ?? '',
      document: settings.document ?? '',
      phone: settings.phone ?? '',
      email: settings.email ?? '',
      address: settings.address ?? '',
      timezone: settings.timezone,
      currency: settings.currency,
      primaryColor: settings.primaryColor ?? '#A3E635',
      secondaryColor: settings.secondaryColor ?? '#0A0F0D',
      allowNegativeStock: settings.allowNegativeStock,
    });
  }

  protected save(): void {
    if (this.form.invalid) {
      this.error.set('Verifique os campos obrigatórios.');
      return;
    }
    const value = this.form.getRawValue();
    const body = {
      ...value,
      tradeName: value.tradeName || null,
      document: value.document || null,
      phone: value.phone || null,
      email: value.email || null,
      address: value.address || null,
    };
    this.saving.set(true);
    this.error.set(null);
    this.http.put<TenantSettings>('/api/settings', body).subscribe({
      next: (settings) => {
        this.saving.set(false);
        this.apply(settings);
        this.toast.success('Configurações salvas.');
        void this.session.refreshTenant();
      },
      error: (error) => {
        this.saving.set(false);
        this.error.set(errorMessage(error));
      },
    });
  }

  protected uploadLogo(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;
    if (file.size > 256 * 1024) {
      this.toast.error('A logo deve ter no máximo 256 KB.');
      return;
    }
    const reader = new FileReader();
    reader.onload = () => {
      this.http.put<TenantSettings>('/api/settings/logo', { dataUrl: reader.result }).subscribe({
        next: (settings) => {
          this.apply(settings);
          this.toast.success('Logo atualizada.');
          void this.session.refreshTenant();
        },
        error: (error) => this.toast.error(errorMessage(error)),
      });
    };
    reader.readAsDataURL(file);
  }

  protected removeLogo(): void {
    this.http.delete<TenantSettings>('/api/settings/logo').subscribe({
      next: (settings) => {
        this.apply(settings);
        void this.session.refreshTenant();
      },
      error: (error) => this.toast.error(errorMessage(error)),
    });
  }
}
