import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, provideRouter, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { TenantMe } from '../models/api.models';
import { accessGuard } from './guards';
import { SessionService } from './session.service';

function snapshot(access: object): ActivatedRouteSnapshot {
  return { data: { access } } as unknown as ActivatedRouteSnapshot;
}

function run(access: object): boolean | UrlTree {
  return TestBed.runInInjectionContext(() => accessGuard(snapshot(access), {} as RouterStateSnapshot)) as boolean | UrlTree;
}

describe('accessGuard', () => {
  let session: SessionService;
  let router: Router;

  const base = (overrides: Partial<TenantMe>): TenantMe => ({
    type: 'TENANT_USER',
    user: { id: 'u', name: 'U', email: 'u@x.com', role: { id: 'r', name: 'R', code: null } },
    tenant: { id: 't', name: 'T', tradeName: 'T', status: 'ACTIVE', timezone: 'America/Sao_Paulo', currency: 'BRL' },
    features: ['PDV'],
    permissions: ['PDV_ACCESS'],
    limits: {},
    tenantAdmin: false,
    operable: true,
    ...overrides,
  });

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    session = TestBed.inject(SessionService);
    router = TestBed.inject(Router);
  });

  it('libera quando há feature e permissão', () => {
    session.me.set(base({}));
    expect(run({ feature: 'PDV', permissions: ['PDV_ACCESS'] })).toBe(true);
  });

  it('leva o administrador à tela de upgrade quando o módulo não foi contratado', () => {
    session.me.set(base({ tenantAdmin: true }));
    const result = run({ feature: 'FINANCIAL', permissions: ['FINANCIAL_READ'] }) as UrlTree;
    expect(router.serializeUrl(result)).toBe('/upgrade?feature=FINANCIAL');
  });

  it('funcionário sem permissão volta para a tela inicial (sem propaganda de upgrade)', () => {
    session.me.set(base({}));
    const result = run({ feature: 'FINANCIAL', permissions: ['FINANCIAL_READ'] }) as UrlTree;
    expect(router.serializeUrl(result)).toBe('/pdv');
    const denied = run({ permissions: ['USER_READ'] }) as UrlTree;
    expect(router.serializeUrl(denied)).toBe('/pdv');
  });
});
