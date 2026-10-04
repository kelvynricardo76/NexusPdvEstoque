import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Logo oficial Nexus: camadas empilhadas dentro de um quadrado arredondado.
 * `wordmark` exibe "NEXUS / PDV & Estoque" ao lado do símbolo.
 */
@Component({
  selector: 'nx-logo',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'nx-logo',
    role: 'img',
    '[attr.aria-label]': '"Nexus PDV & Estoque"',
    '[class.nx-logo--on-light]': 'tone() === "light"',
    '[class.nx-logo--glow]': 'glow()',
    '[style.--nx-logo-size.px]': 'size()',
  },
  template: `
    <svg class="nx-logo__mark" viewBox="0 0 64 64" fill="none" aria-hidden="true">
      <rect x="3" y="3" width="58" height="58" rx="15" class="nx-logo__frame" stroke-width="3.5" />
      <g stroke-width="3.5" stroke-linecap="round" stroke-linejoin="round" class="nx-logo__layers">
        <path d="M32 15 47 23.5 32 32 17 23.5Z" />
        <path d="M17 31.5 32 40 47 31.5" />
        <path d="M17 39.5 32 48 47 39.5" />
      </g>
    </svg>
    @if (wordmark()) {
      <span class="nx-logo__text" aria-hidden="true">
        <span class="nx-logo__name">NEXUS</span>
        <span class="nx-logo__product">PDV &amp; Estoque</span>
      </span>
    }
  `,
  styles: `
    :host {
      --nx-logo-size: 48px;
      display: inline-flex;
      align-items: center;
      gap: calc(var(--nx-logo-size) * 0.28);
    }

    .nx-logo__mark {
      width: var(--nx-logo-size);
      height: var(--nx-logo-size);
      flex-shrink: 0;
    }

    .nx-logo__frame {
      fill: #0b120e;
      stroke: var(--nx-primary);
    }

    .nx-logo__layers {
      stroke: var(--nx-primary);
    }

    :host(.nx-logo--glow) .nx-logo__mark {
      filter: drop-shadow(0 0 10px rgba(163, 230, 53, 0.35));
    }

    .nx-logo__text {
      display: flex;
      flex-direction: column;
      font-family: var(--nx-font-display);
      line-height: 1;
    }

    .nx-logo__name {
      font-weight: 800;
      font-size: calc(var(--nx-logo-size) * 0.56);
      letter-spacing: 0.01em;
      color: #ffffff;
    }

    .nx-logo__product {
      margin-top: calc(var(--nx-logo-size) * 0.06);
      font-weight: 700;
      font-size: calc(var(--nx-logo-size) * 0.3);
      color: var(--nx-primary);
    }

    :host(.nx-logo--on-light) .nx-logo__name {
      color: #0d1411;
    }

    :host(.nx-logo--on-light) .nx-logo__product {
      color: #4d7c0f;
    }
  `,
})
export class NexusLogoComponent {
  readonly size = input<number>(48);
  readonly wordmark = input<boolean>(false);
  readonly tone = input<'dark' | 'light'>('dark');
  readonly glow = input<boolean>(false);
}
