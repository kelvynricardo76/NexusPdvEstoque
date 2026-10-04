import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ButtonComponent, ButtonVariant } from './button.component';

@Component({
  imports: [ButtonComponent],
  template: `<button nxButton [variant]="variant()" [loading]="loading()">Salvar</button>`,
})
class HostComponent {
  readonly variant = signal<ButtonVariant>('primary');
  readonly loading = signal(false);
}

describe('ButtonComponent', () => {
  it('aplica variante e desabilita durante loading', async () => {
    const fixture = TestBed.createComponent(HostComponent);
    await fixture.whenStable();
    const button = (fixture.nativeElement as HTMLElement).querySelector('button')!;

    expect(button.dataset['variant']).toBe('primary');
    expect(button.disabled).toBe(false);
    expect(button.querySelector('.nx-btn__spinner')).toBeNull();

    fixture.componentInstance.variant.set('dark');
    fixture.componentInstance.loading.set(true);
    await fixture.whenStable();

    expect(button.dataset['variant']).toBe('dark');
    expect(button.disabled).toBe(true);
    expect(button.getAttribute('aria-busy')).toBe('true');
    expect(button.querySelector('.nx-btn__spinner')).not.toBeNull();
    expect(button.textContent).toContain('Salvar');
  });
});
