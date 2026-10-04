/** Rótulos em português para os códigos da API. */

import { BadgeTone } from '../../shared/ui/badge/badge.component';

export const PAYMENT_METHOD_LABELS: Record<string, string> = {
  PIX: 'PIX',
  CASH: 'Dinheiro',
  CREDIT_CARD: 'Cartão de crédito',
  DEBIT_CARD: 'Cartão de débito',
  OTHER: 'Outros',
};

export const PAYMENT_METHOD_COLORS: Record<string, string> = {
  PIX: '#A3E635',
  CASH: '#F59E0B',
  CREDIT_CARD: '#3B82F6',
  DEBIT_CARD: '#22D3EE',
  OTHER: '#A78BFA',
};

export const UNIT_LABELS: Record<string, string> = {
  UN: 'Unidade',
  CX: 'Caixa',
  PCT: 'Pacote',
  KG: 'Quilo',
  G: 'Grama',
  L: 'Litro',
  ML: 'Mililitro',
  M: 'Metro',
};

export const STOCK_SITUATION: Record<string, { label: string; tone: BadgeTone }> = {
  NORMAL: { label: 'Normal', tone: 'success' },
  LOW_STOCK: { label: 'Baixo', tone: 'warning' },
  OUT_OF_STOCK: { label: 'Sem estoque', tone: 'danger' },
};

export const SALE_STATUS: Record<string, { label: string; tone: BadgeTone }> = {
  COMPLETED: { label: 'Concluída', tone: 'success' },
  CANCELED: { label: 'Cancelada', tone: 'danger' },
};

export const REFUND_STATUS: Record<string, { label: string; tone: BadgeTone }> = {
  PARTIAL: { label: 'Devolução parcial', tone: 'warning' },
  FULL: { label: 'Devolvida', tone: 'neutral' },
};

export const MOVEMENT_TYPE_LABELS: Record<string, string> = {
  INITIAL: 'Estoque inicial',
  ENTRY: 'Entrada',
  SALE: 'Venda',
  RETURN: 'Devolução',
  POSITIVE_ADJUSTMENT: 'Ajuste (+)',
  NEGATIVE_ADJUSTMENT: 'Ajuste (−)',
  SALE_CANCELLATION: 'Cancelamento de venda',
};

export const FINANCIAL_STATUS: Record<string, { label: string; tone: BadgeTone }> = {
  PENDING: { label: 'Pendente', tone: 'info' },
  PAID: { label: 'Paga', tone: 'success' },
  OVERDUE: { label: 'Vencida', tone: 'danger' },
  CANCELED: { label: 'Cancelada', tone: 'neutral' },
};

export const FINANCIAL_TYPE_LABELS: Record<string, string> = {
  PAYABLE: 'A pagar',
  RECEIVABLE: 'A receber',
};

export const TENANT_STATUS: Record<string, { label: string; tone: BadgeTone }> = {
  TRIAL: { label: 'Trial', tone: 'info' },
  ACTIVE: { label: 'Ativa', tone: 'success' },
  PAST_DUE: { label: 'Inadimplente', tone: 'warning' },
  SUSPENDED: { label: 'Suspensa', tone: 'danger' },
  CANCELED: { label: 'Cancelada', tone: 'neutral' },
};

export const INVOICE_STATUS: Record<string, { label: string; tone: BadgeTone }> = {
  PENDING: { label: 'Pendente', tone: 'info' },
  PAID: { label: 'Paga', tone: 'success' },
  OVERDUE: { label: 'Vencida', tone: 'danger' },
  CANCELED: { label: 'Cancelada', tone: 'neutral' },
};

export const USER_STATUS: Record<string, { label: string; tone: BadgeTone }> = {
  ACTIVE: { label: 'Ativo', tone: 'success' },
  INACTIVE: { label: 'Inativo', tone: 'neutral' },
};

export const FEATURE_LABELS: Record<string, string> = {
  PDV: 'PDV',
  SALES: 'Vendas',
  PRODUCTS: 'Produtos',
  CATEGORIES: 'Categorias',
  STOCK: 'Estoque',
  CUSTOMERS: 'Clientes',
  SUPPLIERS: 'Fornecedores',
  FINANCIAL: 'Financeiro',
  REPORTS: 'Relatórios',
  ADVANCED_REPORTS: 'Relatórios avançados',
  DATA_IMPORT: 'Importação de dados',
  CSV_EXPORT: 'Exportação CSV',
  PDF_EXPORT: 'Exportação PDF',
  ADVANCED_DASHBOARD: 'Dashboard avançado',
  MULTI_BRANCH: 'Multiempresa/filiais',
  BATCH_CONTROL: 'Controle de lotes',
  EXPIRATION_CONTROL: 'Controle de validade',
  SCALE_INTEGRATION: 'Integração com balança',
};

export const LIMIT_LABELS: Record<string, string> = {
  MAX_USERS: 'Usuários',
  MAX_PRODUCTS: 'Produtos',
  MAX_BRANCHES: 'Filiais',
  MAX_MONTHLY_SALES: 'Vendas por mês',
  REPORT_HISTORY_DAYS: 'Histórico de relatórios (dias)',
  STORAGE_LIMIT_MB: 'Armazenamento (MB)',
};

export const AUDIT_ACTION_LABELS: Record<string, string> = {
  LOGIN: 'Login',
  LOGIN_FAILED: 'Falha de login',
  LOGOUT: 'Logout',
  PASSWORD_RESET_REQUEST: 'Pedido de redefinição de senha',
  PASSWORD_CHANGE: 'Troca de senha',
  CREATE: 'Criação',
  UPDATE: 'Alteração',
  DISABLE: 'Desativação',
  ENABLE: 'Ativação',
  DELETE: 'Exclusão',
  SALE: 'Venda',
  SALE_CANCEL: 'Cancelamento de venda',
  SALE_REFUND: 'Devolução de venda',
  STOCK_ENTRY: 'Entrada de estoque',
  STOCK_ADJUST: 'Ajuste de estoque',
  IMPORT: 'Importação',
  EXPORT: 'Exportação',
  ROLE_CHANGE: 'Troca de cargo',
  PERMISSION_CHANGE: 'Alteração de permissões',
  PLAN_CHANGE: 'Troca de plano',
  FEATURE_OVERRIDE: 'Exceção de funcionalidade',
  LIMIT_OVERRIDE: 'Exceção de limite',
  TENANT_CREATE: 'Empresa criada',
  TENANT_SUSPEND: 'Empresa suspensa',
  TENANT_REACTIVATE: 'Empresa reativada',
  TENANT_CANCEL: 'Empresa cancelada',
  SUBSCRIPTION_CHANGE: 'Alteração de assinatura',
  BILLING: 'Cobrança',
};

export const IMPORT_TYPE_LABELS: Record<string, string> = {
  PRODUCTS: 'Produtos',
  CATEGORIES: 'Categorias',
  STOCK: 'Entradas de estoque',
  CUSTOMERS: 'Clientes',
  SUPPLIERS: 'Fornecedores',
};

export function label(map: Record<string, string>, code: string | null | undefined): string {
  return code ? (map[code] ?? code) : '—';
}

/** Limites: -1 = ilimitado. */
export function limitText(value: number | null | undefined): string {
  if (value === null || value === undefined) return '—';
  return value < 0 ? 'Ilimitado' : value.toLocaleString('pt-BR');
}
