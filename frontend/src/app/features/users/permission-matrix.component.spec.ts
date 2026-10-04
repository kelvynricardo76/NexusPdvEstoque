import { TestBed } from '@angular/core/testing';
import { PermissionInfo } from '../../core/models/api.models';
import { PermissionMatrixComponent } from './permission-matrix.component';

const CATALOG: PermissionInfo[] = [
  { code: 'PRODUCT_READ', module: 'PRODUCTS', moduleLabel: 'Produtos', action: 'VIEW', label: 'Ver produtos', feature: 'PRODUCTS', available: true },
  { code: 'PRODUCT_CREATE', module: 'PRODUCTS', moduleLabel: 'Produtos', action: 'CREATE', label: 'Criar produtos', feature: 'PRODUCTS', available: true },
  { code: 'FINANCIAL_READ', module: 'FINANCIAL', moduleLabel: 'Financeiro', action: 'VIEW', label: 'Ver financeiro', feature: 'FINANCIAL', available: false },
  { code: 'SALE_CANCEL', module: 'SALES', moduleLabel: 'Vendas', action: 'SPECIAL', label: 'Cancelar venda', feature: 'SALES', available: true },
];

describe('PermissionMatrixComponent', () => {
  it('agrupa por módulo, bloqueia módulos não contratados e emite a seleção', async () => {
    const fixture = TestBed.createComponent(PermissionMatrixComponent);
    fixture.componentRef.setInput('catalog', CATALOG);
    fixture.componentRef.setInput('selected', new Set(['PRODUCT_READ']));
    const emitted: Set<string>[] = [];
    fixture.componentInstance.selectedChange.subscribe((value) => emitted.push(value));
    await fixture.whenStable();

    const host = fixture.nativeElement as HTMLElement;
    const rows = Array.from(host.querySelectorAll('tbody tr')).map((row) => row.querySelector('td')?.textContent?.trim());
    expect(rows).toEqual(['Produtos', 'Financeiro']);
    expect(host.querySelectorAll('tbody input[type=checkbox]').length).toBe(2);
    expect(host.querySelector('.special')?.textContent).toContain('Cancelar venda');

    const create = host.querySelector<HTMLInputElement>('input[aria-label="Criar produtos"]')!;
    create.checked = true;
    create.dispatchEvent(new Event('change'));
    expect([...emitted[0]].sort()).toEqual(['PRODUCT_CREATE', 'PRODUCT_READ']);
  });
});
