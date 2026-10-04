import { inject } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivateFn, Router, RouterStateSnapshot } from '@angular/router';
import { NavigationService } from '../navigation/navigation.service';
import { SessionService } from './session.service';

/** Dados de rota para o guard de acesso. */
export interface AccessData {
  /** Ao menos uma destas permissões. */
  permissions?: string[];
  feature?: string;
  tenantAdmin?: boolean;
}

/** Usuário de tenant autenticado e tenant operante. */
export const tenantAuthGuard: CanActivateFn = async (_route, state: RouterStateSnapshot) => {
  const session = inject(SessionService);
  const router = inject(Router);
  const me = await session.ensureTenant();
  if (!me) {
    return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
  }
  if (!me.operable) {
    return router.createUrlTree(['/bloqueado']);
  }
  return true;
};

/**
 * Feature do tenant + permissão do usuário (espelho da regra do backend, apenas para UX).
 * Módulo não contratado: administrador vai para a tela de upgrade; demais usuários, para a inicial.
 */
export const accessGuard: CanActivateFn = (route: ActivatedRouteSnapshot) => {
  const session = inject(SessionService);
  const navigation = inject(NavigationService);
  const router = inject(Router);
  const data = (route.data['access'] ?? {}) as AccessData;

  if (data.feature && !session.hasFeature(data.feature)) {
    return session.isTenantAdmin()
      ? router.createUrlTree(['/upgrade'], { queryParams: { feature: data.feature } })
      : router.parseUrl(navigation.landingRoute());
  }
  if (data.tenantAdmin && !session.isTenantAdmin()) {
    return router.parseUrl(navigation.landingRoute());
  }
  if (data.permissions?.length && !session.hasAnyPermission(data.permissions)) {
    return router.parseUrl(navigation.landingRoute());
  }
  return true;
};

export const guestGuard: CanActivateFn = async () => {
  const session = inject(SessionService);
  const navigation = inject(NavigationService);
  const router = inject(Router);
  const me = await session.ensureTenant();
  if (!me) return true;
  return me.operable ? router.parseUrl(navigation.landingRoute()) : router.parseUrl('/bloqueado');
};

export const blockedGuard: CanActivateFn = async () => {
  const session = inject(SessionService);
  const router = inject(Router);
  const me = await session.refreshTenant();
  if (!me) return router.parseUrl('/login');
  return me.operable ? router.parseUrl('/') : true;
};

export const landingGuard: CanActivateFn = async () => {
  const session = inject(SessionService);
  const navigation = inject(NavigationService);
  const router = inject(Router);
  const me = await session.ensureTenant();
  if (!me) return router.parseUrl('/login');
  return router.parseUrl(me.operable ? navigation.landingRoute() : '/bloqueado');
};

export const platformAuthGuard: CanActivateFn = async (_route, state: RouterStateSnapshot) => {
  const session = inject(SessionService);
  const router = inject(Router);
  const me = await session.ensurePlatform();
  return me ? true : router.createUrlTree(['/super-admin/login'], { queryParams: { returnUrl: state.url } });
};

export const platformGuestGuard: CanActivateFn = async () => {
  const session = inject(SessionService);
  const router = inject(Router);
  const me = await session.ensurePlatform();
  return me ? router.parseUrl('/super-admin/dashboard') : true;
};
