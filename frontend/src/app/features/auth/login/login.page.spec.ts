import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { TenantMe } from '../../../core/models/api.models';
import { LoginPage } from './login.page';

const ME: TenantMe = {
  type: 'TENANT_USER',
  user: { id: 'u1', name: 'João Santos', email: 'joao@loja.com', role: { id: 'r1', name: 'Caixa', code: 'CASHIER' } },
  tenant: { id: 't1', name: 'Loja', tradeName: 'Loja Exemplo', status: 'ACTIVE', timezone: 'America/Sao_Paulo', currency: 'BRL' },
  features: ['PDV', 'SALES', 'PRODUCTS'],
  permissions: ['PDV_ACCESS', 'SALE_CREATE', 'PRODUCT_READ'],
  limits: {},
  tenantAdmin: false,
  operable: true,
};

describe('LoginPage', () => {
  let fixture: ComponentFixture<LoginPage>;
  let host: HTMLElement;
  let http: HttpTestingController;

  const query = <T extends Element>(selector: string): T => {
    const element = host.querySelector<T>(selector);
    if (!element) throw new Error(`Elemento não encontrado: ${selector}`);
    return element;
  };

  const type = async (selector: string, value: string): Promise<void> => {
    const input = query<HTMLInputElement>(selector);
    input.value = value;
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();
  };

  const submit = async (): Promise<void> => {
    query<HTMLFormElement>('form').dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  };

  /** Aguarda a conclusão das promessas encadeadas (login → navegação) em modo zoneless. */
  const settle = async (): Promise<void> => {
    await new Promise((resolve) => setTimeout(resolve));
    await fixture.whenStable();
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LoginPage],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(LoginPage);
    host = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  it('exibe a identidade Nexus', () => {
    expect(query('.login__card-title').textContent).toContain('Nexus PDV & Estoque');
    expect(query('.login__headline').textContent).toContain('sob controle.');
    expect(query('.login__footer').textContent).toContain('Nexus Development');
  });

  it('valida campos obrigatórios sem chamar a API', async () => {
    await submit();
    const errors = Array.from(host.querySelectorAll('.nx-field__error')).map((e) => e.textContent?.trim());
    expect(errors).toEqual(['Informe seu e-mail ou usuário.', 'Informe sua senha.']);
    http.expectNone('/api/auth/login');
  });

  it('atualiza a mensagem quando o erro do campo muda', async () => {
    await submit();
    await type('#login-email', 'e-mail inválido');
    expect(query('.nx-field__error').textContent).toContain('Informe um e-mail ou usuário válido.');
  });

  it('aceita nome de usuário no login da empresa', async () => {
    await type('#login-email', 'admin');
    await type('#login-password', 'admin');
    await submit();
    expect(host.querySelector('.nx-field__error')).toBeNull();
    const request = http.expectOne('/api/auth/login');
    expect(request.request.body).toEqual({ email: 'admin', password: 'admin' });
  });

  it('alterna a visibilidade da senha', async () => {
    const password = query<HTMLInputElement>('#login-password');
    query<HTMLButtonElement>('.login__toggle').click();
    await fixture.whenStable();
    expect(password.type).toBe('text');
  });

  it('autentica e leva o caixa direto ao PDV', async () => {
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    await type('#login-email', 'joao@loja.com');
    await type('#login-password', 'Senha2026');
    await submit();

    const request = http.expectOne('/api/auth/login');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ email: 'joao@loja.com', password: 'Senha2026' });
    request.flush(ME);
    await settle();

    expect(navigate).toHaveBeenCalledWith('/pdv');
  });

  it('mostra o erro retornado pela API', async () => {
    await type('#login-email', 'joao@loja.com');
    await type('#login-password', 'errada123');
    await submit();
    http.expectOne('/api/auth/login').flush(
      { code: 'INVALID_CREDENTIALS', message: 'E-mail ou senha inválidos.' },
      { status: 401, statusText: 'Unauthorized' },
    );
    await settle();
    expect(query('nx-alert').textContent).toContain('E-mail ou senha inválidos.');
  });
});
