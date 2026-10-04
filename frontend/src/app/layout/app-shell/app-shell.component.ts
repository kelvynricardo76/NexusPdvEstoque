import { HttpClient } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  HostListener,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { SessionService } from '../../core/auth/session.service';
import { NotificationItem, SearchResponse } from '../../core/models/api.models';
import { NavigationService } from '../../core/navigation/navigation.service';
import { params } from '../../core/util/http-params';
import { IconComponent } from '../../shared/ui/icon/icon.component';
import { NexusLogoComponent } from '../../shared/ui/logo/nexus-logo.component';

@Component({
  selector: 'nx-app-shell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, IconComponent, NexusLogoComponent],
  templateUrl: './app-shell.component.html',
  styleUrl: './app-shell.component.scss',
})
export class AppShellComponent implements OnInit {
  private readonly http = inject(HttpClient);
  protected readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly session = inject(SessionService);
  protected readonly navigation = inject(NavigationService);

  protected readonly drawerOpen = signal(false);
  protected readonly userMenuOpen = signal(false);
  protected readonly notificationsOpen = signal(false);
  protected readonly notifications = signal<NotificationItem[]>([]);

  protected readonly query = signal('');
  protected readonly results = signal<SearchResponse | null>(null);
  protected readonly searchOpen = signal(false);
  private searchTimer: ReturnType<typeof setTimeout> | null = null;

  protected readonly initials = computed(() => {
    const parts = (this.session.me()?.user.name ?? '').trim().split(/\s+/);
    return ((parts[0]?.[0] ?? '') + (parts.length > 1 ? parts[parts.length - 1][0] : '')).toUpperCase();
  });

  protected readonly hasResults = computed(() => {
    const r = this.results();
    return !!r && r.products.length + r.customers.length + r.sales.length > 0;
  });

  protected readonly subscriptionWarning = computed(() => {
    const me = this.session.me();
    if (!me?.tenantAdmin || !me.subscription) return null;
    if (me.subscription.status === 'PAST_DUE') return 'Sua assinatura está com pagamento em atraso. Regularize para evitar a suspensão.';
    return null;
  });

  ngOnInit(): void {
    this.loadNotifications();
    this.router.events
      .pipe(filter((event) => event instanceof NavigationEnd), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.drawerOpen.set(false);
        this.userMenuOpen.set(false);
        this.notificationsOpen.set(false);
        this.searchOpen.set(false);
      });
  }

  protected loadNotifications(): void {
    this.http.get<NotificationItem[]>('/api/notifications').subscribe({
      next: (items) => this.notifications.set(items),
      error: () => this.notifications.set([]),
    });
  }

  protected onSearch(value: string): void {
    this.query.set(value);
    if (this.searchTimer) clearTimeout(this.searchTimer);
    if (value.trim().length < 2) {
      this.results.set(null);
      return;
    }
    this.searchTimer = setTimeout(() => {
      this.http.get<SearchResponse>('/api/search', { params: params({ q: value.trim() }) }).subscribe({
        next: (response) => {
          this.results.set(response);
          this.searchOpen.set(true);
        },
        error: () => this.results.set(null),
      });
    }, 250);
  }

  protected openResult(route: string, id: string): void {
    this.query.set('');
    this.results.set(null);
    this.searchOpen.set(false);
    void this.router.navigate([route, id]);
  }

  protected toggleNotifications(): void {
    this.userMenuOpen.set(false);
    this.notificationsOpen.update((open) => !open);
    if (this.notificationsOpen()) this.loadNotifications();
  }

  protected toggleUserMenu(): void {
    this.notificationsOpen.set(false);
    this.userMenuOpen.update((open) => !open);
  }

  protected async logout(): Promise<void> {
    await this.session.logout();
    void this.router.navigate(['/login']);
  }

  @HostListener('document:keydown.escape')
  protected closeOverlays(): void {
    this.drawerOpen.set(false);
    this.userMenuOpen.set(false);
    this.notificationsOpen.set(false);
    this.searchOpen.set(false);
  }
}
