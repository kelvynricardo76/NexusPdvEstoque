import { IconName } from '../../shared/ui/icon/icons';

/**
 * Item do menu dinâmico: visível quando o tenant possui a feature E o usuário possui ao
 * menos uma das permissões. TENANT_ADMIN vê itens não contratados com cadeado (UX de upgrade).
 */
export interface MenuItem {
  readonly label: string;
  readonly icon: IconName;
  readonly route: string;
  readonly permissions: readonly string[];
  readonly feature?: string;
  readonly mobile?: boolean;
}

export const TENANT_MENU: readonly MenuItem[] = [
  { label: 'Início', icon: 'home', route: '/inicio', permissions: ['DASHBOARD_VIEW'], mobile: true },
  { label: 'PDV', icon: 'cart', route: '/pdv', permissions: ['PDV_ACCESS'], feature: 'PDV', mobile: true },
  { label: 'Vendas', icon: 'receipt', route: '/vendas', permissions: ['SALE_READ'], feature: 'SALES' },
  { label: 'Estoque', icon: 'package', route: '/estoque', permissions: ['STOCK_READ'], feature: 'STOCK', mobile: true },
  { label: 'Produtos', icon: 'tag', route: '/produtos', permissions: ['PRODUCT_READ'], feature: 'PRODUCTS', mobile: true },
  { label: 'Categorias', icon: 'folder', route: '/categorias', permissions: ['CATEGORY_READ'], feature: 'CATEGORIES' },
  { label: 'Clientes', icon: 'users', route: '/clientes', permissions: ['CUSTOMER_READ'], feature: 'CUSTOMERS' },
  { label: 'Fornecedores', icon: 'truck', route: '/fornecedores', permissions: ['SUPPLIER_READ'], feature: 'SUPPLIERS' },
  { label: 'Financeiro', icon: 'wallet', route: '/financeiro', permissions: ['FINANCIAL_READ'], feature: 'FINANCIAL' },
  {
    label: 'Relatórios',
    icon: 'bar-chart',
    route: '/relatorios',
    permissions: ['REPORT_BASIC_READ', 'REPORT_ADVANCED_READ'],
    feature: 'REPORTS',
  },
  { label: 'Importação', icon: 'upload', route: '/importacao', permissions: ['IMPORT_EXECUTE'], feature: 'DATA_IMPORT' },
  { label: 'Usuários', icon: 'user-cog', route: '/usuarios', permissions: ['USER_READ'] },
  { label: 'Auditoria', icon: 'file-text', route: '/auditoria', permissions: ['AUDIT_READ'] },
  { label: 'Configurações', icon: 'settings', route: '/configuracoes', permissions: [] },
];

export const SUPER_ADMIN_MENU: readonly MenuItem[] = [
  { label: 'Dashboard', icon: 'home', route: '/super-admin/dashboard', permissions: [] },
  { label: 'Empresas', icon: 'building', route: '/super-admin/empresas', permissions: [] },
  { label: 'Planos', icon: 'layers', route: '/super-admin/planos', permissions: [] },
  { label: 'Funcionalidades', icon: 'toggle', route: '/super-admin/funcionalidades', permissions: [] },
  { label: 'Assinaturas', icon: 'repeat', route: '/super-admin/assinaturas', permissions: [] },
  { label: 'Cobranças', icon: 'credit-card', route: '/super-admin/cobrancas', permissions: [] },
  { label: 'Administradores', icon: 'shield-check', route: '/super-admin/administradores', permissions: [] },
  { label: 'Auditoria', icon: 'file-text', route: '/super-admin/auditoria', permissions: [] },
  { label: 'Configurações', icon: 'settings', route: '/super-admin/configuracoes', permissions: [] },
];
