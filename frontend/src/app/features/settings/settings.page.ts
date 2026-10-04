import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { SessionService } from '../../core/auth/session.service';
import { PageHeaderComponent } from '../../shared/ui/states/states.components';

@Component({
  selector: 'nx-settings-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, PageHeaderComponent],
  template: `
    <div class="nx-page">
      <nx-page-header title="Configurações" subtitle="Empresa, assinatura e sua conta" />
      <nav class="nx-tabs">
        <a routerLink="empresa" routerLinkActive="is-active">Empresa</a>
        @if (session.isTenantAdmin()) {
          <a routerLink="assinatura" routerLinkActive="is-active">Assinatura</a>
        }
        <a routerLink="conta" routerLinkActive="is-active">Minha conta</a>
      </nav>
      <router-outlet />
    </div>
  `,
})
export class SettingsPage {
  protected readonly session = inject(SessionService);
}
