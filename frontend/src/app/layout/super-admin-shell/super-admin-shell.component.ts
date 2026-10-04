import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { SessionService } from '../../core/auth/session.service';
import { SUPER_ADMIN_MENU } from '../../core/navigation/menu';
import { IconComponent } from '../../shared/ui/icon/icon.component';
import { NexusLogoComponent } from '../../shared/ui/logo/nexus-logo.component';

/** Shell do painel exclusivo da Nexus Development (SUPER_ADMIN). */
@Component({
  selector: 'nx-super-admin-shell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, IconComponent, NexusLogoComponent],
  template: `
    <div class="shell">
      <aside class="sidebar" [class.is-open]="drawerOpen()">
        <div class="brand">
          <nx-logo [size]="38" />
          <div><strong>NEXUS</strong><span>Painel da plataforma</span></div>
        </div>
        <nav>
          @for (item of menu; track item.route) {
            <a [routerLink]="item.route" routerLinkActive="is-active" (click)="drawerOpen.set(false)">
              <nx-icon [name]="item.icon" [size]="18" /> {{ item.label }}
            </a>
          }
        </nav>
        <div class="me">
          <span class="me__name">{{ session.platformMe()?.name }}</span>
          <span class="me__email">{{ session.platformMe()?.email }}</span>
          <button type="button" (click)="logout()"><nx-icon name="log-out" [size]="16" /> Sair</button>
        </div>
      </aside>
      @if (drawerOpen()) { <div class="backdrop" (click)="drawerOpen.set(false)"></div> }
      <div class="main">
        <header class="topbar">
          <button type="button" class="menu" (click)="drawerOpen.set(true)" aria-label="Abrir menu"><nx-icon name="menu" [size]="20" /></button>
          <span class="badge">SUPER ADMIN • Nexus Development</span>
        </header>
        <main><router-outlet /></main>
      </div>
    </div>
  `,
  styles: `
    .shell { display: flex; min-height: 100dvh; }
    .sidebar {
      position: sticky; top: 0; display: flex; flex-direction: column; flex-shrink: 0; width: 248px; height: 100dvh;
      padding: var(--nx-space-5) var(--nx-space-3); border-right: 1px solid var(--nx-border); background: var(--nx-bg-elevated); overflow-y: auto;
    }
    .brand { display: flex; align-items: center; gap: var(--nx-space-3); padding: 0 var(--nx-space-3) var(--nx-space-6); }
    .brand div { display: flex; flex-direction: column; line-height: 1.1; }
    .brand strong { font-family: var(--nx-font-display); font-size: var(--nx-text-lg); }
    .brand span { color: var(--nx-primary); font-size: var(--nx-text-xs); font-weight: 600; }
    nav { display: flex; flex: 1; flex-direction: column; gap: 2px; }
    nav a { display: flex; align-items: center; gap: var(--nx-space-3); padding: 10px var(--nx-space-3); border-radius: var(--nx-radius-md); color: var(--nx-text-muted); font-size: var(--nx-text-sm); font-weight: 500; text-decoration: none; }
    nav a:hover { background: var(--nx-surface-2); color: var(--nx-text); text-decoration: none; }
    nav a.is-active { background: var(--nx-primary); color: var(--nx-on-primary); font-weight: 600; }
    .me { display: flex; flex-direction: column; gap: 2px; margin-top: var(--nx-space-4); padding: var(--nx-space-4); border: 1px solid var(--nx-border); border-radius: var(--nx-radius-lg); font-size: var(--nx-text-sm); }
    .me__email { color: var(--nx-text-muted); font-size: var(--nx-text-xs); word-break: break-all; }
    .me button { display: inline-flex; align-items: center; gap: 6px; margin-top: var(--nx-space-2); padding: 0; border: 0; background: transparent; color: var(--nx-danger); font-size: var(--nx-text-xs); font-weight: 600; cursor: pointer; }
    .main { display: flex; flex: 1; flex-direction: column; min-width: 0; }
    .topbar { position: sticky; top: 0; z-index: 30; display: flex; align-items: center; gap: var(--nx-space-3); height: 56px; padding: 0 var(--nx-space-6); border-bottom: 1px solid var(--nx-border); background: rgba(10, 15, 13, 0.9); backdrop-filter: blur(10px); }
    .badge { padding: 4px 10px; border: 1px solid var(--nx-primary-line); border-radius: 999px; background: var(--nx-primary-soft); color: var(--nx-primary); font-size: var(--nx-text-xs); font-weight: 700; letter-spacing: 0.06em; }
    .menu { display: none; padding: 6px; border: 0; background: transparent; color: var(--nx-text); cursor: pointer; }
    .backdrop { position: fixed; inset: 0; z-index: 55; background: rgba(0, 0, 0, 0.55); }
    @media (max-width: 959px) {
      .sidebar { position: fixed; z-index: 60; transform: translateX(-100%); transition: transform 200ms var(--nx-ease); }
      .sidebar.is-open { transform: none; }
      .menu { display: inline-flex; }
      .topbar { padding: 0 var(--nx-space-3); }
    }
  `,
})
export class SuperAdminShellComponent {
  protected readonly session = inject(SessionService);
  private readonly router = inject(Router);
  protected readonly menu = SUPER_ADMIN_MENU;
  protected readonly drawerOpen = signal(false);

  protected async logout(): Promise<void> {
    await this.session.logoutPlatform();
    await this.router.navigate(['/super-admin/login']);
  }
}
