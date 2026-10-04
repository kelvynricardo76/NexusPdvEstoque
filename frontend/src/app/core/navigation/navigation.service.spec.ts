import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { SessionService } from '../auth/session.service';
import { TenantMe } from '../models/api.models';
import { NavigationService } from './navigation.service';

function me(overrides: Partial<TenantMe>): TenantMe {
  return {
    type: 'TENANT_USER',
    user: { id: 'u', name: 'Maria', email: 'm@x.com', role: { id: 'r', name: 'X', code: null } },
    tenant: { id: 't', name: 'T', tradeName: 'T', status: 'ACTIVE', timezone: 'America/Sao_Paulo', currency: 'BRL' },
    features: [],
    permissions: [],
    limits: {},
    tenantAdmin: false,
    operable: true,
    ...overrides,
  };
}

describe('NavigationService (menu dinâmico)', () => {
  let session: SessionService;
  let navigation: NavigationService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    session = TestBed.inject(SessionService);
    navigation = TestBed.inject(NavigationService);
  });

  it('exibe Financeiro somente com feature do tenant + permissão do usuário', () => {
    session.me.set(me({ features: ['FINANCIAL'], permissions: ['FINANCIAL_READ'] }));
    expect(navigation.items().map((item) => item.label)).toContain('Financeiro');

    session.me.set(me({ features: ['FINANCIAL'], permissions: [] }));
    expect(navigation.items().map((item) => item.label)).not.toContain('Financeiro');

    session.me.set(me({ features: [], permissions: ['FINANCIAL_READ'] }));
    expect(navigation.items().map((item) => item.label)).not.toContain('Financeiro');
  });

  it('mostra módulos não contratados com cadeado apenas para o administrador', () => {
    session.me.set(me({ tenantAdmin: true, features: ['PDV'], permissions: ['PDV_ACCESS', 'FINANCIAL_READ'] }));
    const financial = navigation.items().find((item) => item.label === 'Financeiro');
    expect(financial?.locked).toBe(true);

    session.me.set(me({ tenantAdmin: false, features: ['PDV'], permissions: ['PDV_ACCESS'] }));
    expect(navigation.items().find((item) => item.label === 'Financeiro')).toBeUndefined();
  });

  it('define a tela inicial pela primeira permissão disponível', () => {
    session.me.set(me({ features: ['PDV'], permissions: ['PDV_ACCESS'] }));
    expect(navigation.landingRoute()).toBe('/pdv');
    session.me.set(me({ features: ['PDV'], permissions: ['DASHBOARD_VIEW', 'PDV_ACCESS'] }));
    expect(navigation.landingRoute()).toBe('/inicio');
  });
});
