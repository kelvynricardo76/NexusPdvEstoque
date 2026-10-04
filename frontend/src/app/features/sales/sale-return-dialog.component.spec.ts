import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Sale } from '../../core/models/api.models';
import { SaleReturnDialogComponent } from './sale-return-dialog.component';

const SALE: Sale = {
  id: 'sale-1',
  number: 7,
  createdAt: '2026-09-29T12:00:00Z',
  operatorId: 'op',
  operatorName: 'Operador',
  subtotal: 30,
  discount: 1,
  total: 29,
  changeAmount: 0,
  status: 'COMPLETED',
  refundStatus: 'PARTIAL',
  refundedTotal: 9.67,
  items: [
    { productId: 'p1', lineNumber: 1, description: 'Produto A', quantity: 3, unitPrice: 10, discount: 0, total: 30, returnedQuantity: 1 },
  ],
  payments: [{ method: 'PIX', amount: 29, status: 'CONFIRMED' }],
  returns: [],
};

describe('SaleReturnDialogComponent', () => {
  it('limita à quantidade devolvível, estima o estorno proporcional e envia com Idempotency-Key', async () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const fixture = TestBed.createComponent(SaleReturnDialogComponent);
    fixture.componentRef.setInput('sale', SALE);
    await fixture.whenStable();

    const host = fixture.nativeElement as HTMLElement;
    const submit = () => Array.from(host.querySelectorAll('button')).find((b) => b.textContent?.includes('Registrar devolução'))!;
    expect(host.querySelector('td.num')?.textContent?.trim()).toBe('2');
    expect(submit().disabled).toBe(true);

    const quantity = host.querySelector<HTMLInputElement>('input[type=number]')!;
    quantity.value = '3';
    quantity.dispatchEvent(new Event('input'));
    const reason = host.querySelector<HTMLTextAreaElement>('textarea')!;
    reason.value = 'Defeito';
    reason.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    expect(submit().disabled).toBe(true); // 3 > 2 disponíveis

    quantity.value = '1';
    quantity.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    expect(host.querySelector('.estimate')?.textContent).toMatch(/9[.,]67/);
    expect(submit().disabled).toBe(false);

    submit().click();
    const request = TestBed.inject(HttpTestingController).expectOne('/api/sales/sale-1/returns');
    expect(request.request.headers.get('Idempotency-Key')).toMatch(/^ret-/);
    expect(request.request.body).toEqual({
      reason: 'Defeito',
      refundMethod: 'CASH',
      items: [{ lineNumber: 1, quantity: 1, restock: true }],
    });
  });
});
