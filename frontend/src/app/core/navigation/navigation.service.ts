import { computed, inject, Injectable } from '@angular/core';
import { SessionService } from '../auth/session.service';
import { MenuItem, TENANT_MENU } from './menu';

export interface VisibleMenuItem extends MenuItem {
  readonly locked: boolean;
}

/** Menu dinâmico derivado de feature do tenant + permissão do usuário. */
@Injectable({ providedIn: 'root' })
export class NavigationService {
  private readonly session = inject(SessionService);

  readonly items = computed<VisibleMenuItem[]>(() => {
    const me = this.session.me();
    if (!me) return [];
    const result: VisibleMenuItem[] = [];
    for (const item of TENANT_MENU) {
      const featureOk = !item.feature || this.session.hasFeature(item.feature);
      const permissionOk = item.permissions.length === 0 || this.session.hasAnyPermission(item.permissions);
      if (featureOk && permissionOk) {
        result.push({ ...item, locked: false });
      } else if (!featureOk && me.tenantAdmin) {
        // Administrador vê o módulo não contratado com cadeado (UX de upgrade).
        result.push({ ...item, locked: true });
      }
    }
    return result;
  });

  readonly mobileItems = computed(() => this.items().filter((item) => item.mobile && !item.locked).slice(0, 4));

  /** Primeira tela acessível para o usuário (ex.: caixa vai direto ao PDV). */
  landingRoute(): string {
    return this.items().find((item) => !item.locked)?.route ?? '/configuracoes';
  }
}
