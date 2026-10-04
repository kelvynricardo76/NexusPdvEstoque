import { HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { PlatformMe, TenantMe } from '../models/api.models';

/**
 * Sessão do usuário (tenant ou Super Admin). A autenticação é por cookie HttpOnly no servidor;
 * aqui mantemos apenas a visão retornada por /me para montar a interface.
 * O frontend NÃO é camada de segurança: o backend revalida cada requisição.
 */
@Injectable({ providedIn: 'root' })
export class SessionService {
  private readonly http = inject(HttpClient);

  readonly me = signal<TenantMe | null>(null);
  readonly platformMe = signal<PlatformMe | null>(null);
  private tenantLoaded = false;
  private platformLoaded = false;

  readonly permissions = computed(() => new Set(this.me()?.permissions ?? []));
  readonly features = computed(() => new Set(this.me()?.features ?? []));
  readonly isTenantAdmin = computed(() => this.me()?.tenantAdmin ?? false);
  readonly firstName = computed(() => (this.me()?.user.name ?? '').split(' ')[0]);

  hasPermission(code: string): boolean {
    return this.permissions().has(code);
  }

  hasAnyPermission(codes: readonly string[]): boolean {
    return codes.some((code) => this.permissions().has(code));
  }

  hasFeature(code: string): boolean {
    return this.features().has(code);
  }

  /** Carrega a sessão de tenant (uma vez por navegação inicial). */
  async ensureTenant(): Promise<TenantMe | null> {
    if (this.tenantLoaded) return this.me();
    return this.refreshTenant();
  }

  async refreshTenant(): Promise<TenantMe | null> {
    try {
      const me = await firstValueFrom(this.http.get<TenantMe>('/api/auth/me'));
      this.me.set(me);
    } catch {
      this.me.set(null);
    }
    this.tenantLoaded = true;
    return this.me();
  }

  async login(email: string, password: string): Promise<TenantMe> {
    const me = await firstValueFrom(this.http.post<TenantMe>('/api/auth/login', { email, password }));
    this.me.set(me);
    this.tenantLoaded = true;
    return me;
  }

  async logout(): Promise<void> {
    try {
      await firstValueFrom(this.http.post<void>('/api/auth/logout', {}));
    } finally {
      this.clearTenant();
    }
  }

  clearTenant(): void {
    this.me.set(null);
    this.tenantLoaded = true;
  }

  async ensurePlatform(): Promise<PlatformMe | null> {
    if (this.platformLoaded) return this.platformMe();
    try {
      this.platformMe.set(await firstValueFrom(this.http.get<PlatformMe>('/api/super-admin/auth/me')));
    } catch {
      this.platformMe.set(null);
    }
    this.platformLoaded = true;
    return this.platformMe();
  }

  async loginPlatform(email: string, password: string): Promise<PlatformMe> {
    const me = await firstValueFrom(this.http.post<PlatformMe>('/api/super-admin/auth/login', { email, password }));
    this.platformMe.set(me);
    this.platformLoaded = true;
    return me;
  }

  async logoutPlatform(): Promise<void> {
    try {
      await firstValueFrom(this.http.post<void>('/api/super-admin/auth/logout', {}));
    } finally {
      this.clearPlatform();
    }
  }

  clearPlatform(): void {
    this.platformMe.set(null);
    this.platformLoaded = true;
  }
}
