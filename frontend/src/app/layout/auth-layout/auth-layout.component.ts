import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { IconComponent } from '../../shared/ui/icon/icon.component';
import { IconName } from '../../shared/ui/icon/icons';
import { NexusLogoComponent } from '../../shared/ui/logo/nexus-logo.component';

interface Highlight {
  readonly icon: IconName;
  readonly title: string;
  readonly description: string;
}

/**
 * Layout das telas de acesso (login, recuperação de senha, Super Admin): painel da marca
 * Nexus à esquerda e o card de ação à direita (conteúdo projetado).
 */
@Component({
  selector: 'nx-auth-layout',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, NexusLogoComponent],
  templateUrl: './auth-layout.component.html',
  styleUrl: './auth-layout.component.scss',
})
export class AuthLayoutComponent {
  readonly eyebrow = input<string>('Sistema de PDV e estoque para o seu negócio');

  protected readonly currentYear = new Date().getFullYear();

  protected readonly highlights: readonly Highlight[] = [
    { icon: 'cart', title: 'Mais Vendas', description: 'Frente de caixa rápida, com leitor de código de barras.' },
    { icon: 'package', title: 'Mais Controle', description: 'Acompanhe vendas, estoque e financeiro em tempo real.' },
    { icon: 'bar-chart', title: 'Mais Crescimento', description: 'Relatórios completos para tomar as melhores decisões.' },
  ];
}
