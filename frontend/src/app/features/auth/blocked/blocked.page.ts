import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { Router } from '@angular/router';
import { SessionService } from '../../../core/auth/session.service';
import { AuthLayoutComponent } from '../../../layout/auth-layout/auth-layout.component';
import { AlertComponent } from '../../../shared/ui/alert/alert.component';
import { ButtonComponent } from '../../../shared/ui/button/button.component';
import { NexusLogoComponent } from '../../../shared/ui/logo/nexus-logo.component';

/** Tenant suspenso, cancelado ou com assinatura inativa: acesso operacional bloqueado. */
@Component({
  selector: 'nx-blocked-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AuthLayoutComponent, AlertComponent, ButtonComponent, NexusLogoComponent],
  template: `
    <nx-auth-layout>
      <header class="login__card-header">
        <nx-logo [size]="52" />
        <div>
          <h2 class="login__card-title">{{ title() }}</h2>
          <p class="login__card-subtitle">{{ session.me()?.tenant?.tradeName }}</p>
        </div>
      </header>
      <nx-alert tone="warning">{{ message() }}</nx-alert>
      <p class="login__back">Dúvidas? Fale com a Nexus Development pelo e-mail <strong>suporte&#64;nexusdevelopment.com.br</strong>.</p>
      <button nxButton type="button" variant="dark" size="lg" [block]="true" class="login__submit" (click)="logout()">Sair</button>
    </nx-auth-layout>
  `,
})
export class BlockedPage {
  protected readonly session = inject(SessionService);
  private readonly router = inject(Router);

  protected readonly title = computed(() =>
    this.session.me()?.blockReason === 'SUBSCRIPTION_INACTIVE' ? 'Assinatura inativa' : 'Acesso suspenso',
  );

  protected readonly message = computed(() =>
    this.session.me()?.blockReason === 'SUBSCRIPTION_INACTIVE'
      ? 'O período de teste terminou ou a assinatura não está ativa. Regularize a assinatura para voltar a operar.'
      : 'O acesso desta empresa está suspenso. Entre em contato com a Nexus Development para reativá-lo.',
  );

  protected async logout(): Promise<void> {
    await this.session.logout();
    await this.router.navigate(['/login']);
  }
}
