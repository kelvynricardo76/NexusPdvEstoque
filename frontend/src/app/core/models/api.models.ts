/** Contratos da API Nexus PDV & Estoque (espelham os DTOs do backend). */

export interface ApiError {
  code: string;
  message: string;
  timestamp?: string;
  traceId?: string;
  fields?: { field: string; message: string }[];
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export type PeriodPreset = 'TODAY' | 'LAST_7_DAYS' | 'LAST_30_DAYS' | 'CUSTOM';

// ---------- Sessão ----------

export interface TenantMe {
  type: 'TENANT_USER';
  user: { id: string; name: string; email: string; role: { id: string; name: string; code: string | null } };
  tenant: {
    id: string;
    name: string;
    tradeName: string;
    status: string;
    logoDataUrl?: string;
    primaryColor?: string;
    timezone: string;
    currency: string;
  };
  subscription?: {
    planCode: string;
    planName: string;
    status: string;
    trialEndDate?: string;
    currentPeriodEnd?: string;
  };
  features: string[];
  permissions: string[];
  /** -1 = ilimitado */
  limits: Record<string, number>;
  tenantAdmin: boolean;
  operable: boolean;
  blockReason?: string;
}

export interface PlatformMe {
  type: 'PLATFORM_ADMIN';
  id: string;
  name: string;
  email: string;
}

// ---------- Catálogo ----------

export type ProductUnit = 'UN' | 'CX' | 'PCT' | 'KG' | 'G' | 'L' | 'ML' | 'M';
export type StockSituation = 'NORMAL' | 'LOW_STOCK' | 'OUT_OF_STOCK';

export interface Product {
  id: string;
  name: string;
  description?: string;
  sku?: string;
  barcode?: string;
  categoryId?: string;
  categoryName?: string;
  supplierId?: string;
  supplierName?: string;
  salePrice: number;
  costPrice?: number;
  currentStock: number;
  minimumStock: number;
  unit: ProductUnit;
  active: boolean;
  situation: StockSituation;
  createdAt: string;
  updatedAt: string;
}

export interface ProductRequest {
  name: string;
  description?: string | null;
  sku?: string | null;
  barcode?: string | null;
  categoryId?: string | null;
  supplierId?: string | null;
  salePrice: number;
  costPrice?: number | null;
  minimumStock?: number | null;
  initialStock?: number | null;
  unit: ProductUnit;
}

export interface Category {
  id: string;
  name: string;
  description?: string;
  active: boolean;
  productCount: number;
}

export interface Supplier {
  id: string;
  legalName: string;
  tradeName?: string;
  document?: string;
  phone?: string;
  email?: string;
  address?: string;
  notes?: string;
  active: boolean;
  createdAt: string;
}

export interface StockItem {
  productId: string;
  name: string;
  sku?: string;
  categoryName?: string;
  currentStock: number;
  minimumStock: number;
  unit: ProductUnit;
  situation: StockSituation;
  active: boolean;
}

export interface StockMovement {
  id: string;
  productId: string;
  productName: string;
  type: string;
  quantity: number;
  previousStock: number;
  newStock: number;
  referenceType?: string;
  referenceId?: string;
  reason?: string;
  userName?: string;
  createdAt: string;
}

export interface StockSummary {
  inStock: number;
  lowStock: number;
  outOfStock: number;
}

// ---------- Clientes ----------

export interface CustomerSummary {
  id: string;
  name: string;
  documentMasked?: string;
  phone?: string;
  email?: string;
  active: boolean;
}

export interface CustomerDetail {
  id: string;
  name: string;
  document?: string;
  phone?: string;
  email?: string;
  address?: string;
  notes?: string;
  active: boolean;
  createdAt: string;
  purchases: number;
  totalPurchased: number;
  lastPurchaseAt?: string;
  recentPurchases: { saleId: string; number: number; createdAt: string; total: number; status: string }[];
}

export interface CustomerRequest {
  name: string;
  document?: string | null;
  phone?: string | null;
  email?: string | null;
  address?: string | null;
  notes?: string | null;
}

// ---------- Vendas ----------

export type PaymentMethod = 'PIX' | 'CASH' | 'CREDIT_CARD' | 'DEBIT_CARD' | 'OTHER';

export interface SaleSummary {
  id: string;
  number: number;
  createdAt: string;
  customerName?: string;
  operatorName: string;
  total: number;
  status: 'COMPLETED' | 'CANCELED';
  refundStatus?: SaleRefundStatus;
}

export type SaleRefundStatus = 'NONE' | 'PARTIAL' | 'FULL';

export interface SaleReturn {
  id: string;
  createdAt: string;
  reason: string;
  refundMethod: PaymentMethod;
  total: number;
  userName: string;
  items: { lineNumber: number; description: string; quantity: number; amount: number; restocked: boolean }[];
}

export interface SaleReturnRequest {
  reason: string;
  refundMethod: PaymentMethod;
  items: { lineNumber: number; quantity: number; restock: boolean }[];
}

export interface Sale {
  id: string;
  number: number;
  createdAt: string;
  customerId?: string;
  customerName?: string;
  operatorId: string;
  operatorName: string;
  subtotal: number;
  discount: number;
  total: number;
  changeAmount: number;
  status: 'COMPLETED' | 'CANCELED';
  cancelReason?: string;
  canceledAt?: string;
  canceledByName?: string;
  refundStatus: SaleRefundStatus;
  refundedTotal: number;
  items: {
    productId: string;
    lineNumber: number;
    description: string;
    sku?: string;
    quantity: number;
    unitPrice: number;
    discount: number;
    total: number;
    returnedQuantity: number;
  }[];
  payments: { method: PaymentMethod; amount: number; status: string }[];
  returns: SaleReturn[];
}

export interface Receipt {
  companyName?: string;
  tradeName?: string;
  document?: string;
  address?: string;
  phone?: string;
  logoDataUrl?: string;
  primaryColor?: string;
  sale: Sale;
}

export interface FinalizeSaleRequest {
  items: { productId: string; quantity: number; discount?: number | null }[];
  customerId?: string | null;
  discount?: number | null;
  payments: { method: PaymentMethod; amount: number }[];
}

// ---------- Financeiro ----------

export type FinancialType = 'PAYABLE' | 'RECEIVABLE';
export type FinancialStatus = 'PENDING' | 'PAID' | 'OVERDUE' | 'CANCELED';

export interface FinancialEntry {
  id: string;
  type: FinancialType;
  description: string;
  category?: string;
  amount: number;
  dueDate: string;
  paymentDate?: string;
  status: FinancialStatus;
  notes?: string;
  customerId?: string;
  customerName?: string;
  supplierId?: string;
  supplierName?: string;
  createdAt: string;
}

export interface FinancialSummary {
  from: string;
  to: string;
  revenue: number;
  expenses: number;
  balance: number;
  receivablePending: number;
  receivablePendingCount: number;
  receivableOverdue: number;
  receivableOverdueCount: number;
  payablePending: number;
  payablePendingCount: number;
  payableOverdue: number;
  payableOverdueCount: number;
}

// ---------- Dashboard ----------

export interface DailySales {
  date: string;
  total: number;
  count: number;
}

export interface Dashboard {
  from: string;
  to: string;
  kpis: {
    salesTotal: number;
    salesTotalVariation?: number;
    salesCount: number;
    salesCountVariation?: number;
    averageTicket: number;
    productsInStock: number;
    lowStock: number;
    outOfStock: number;
    activeCustomers: number;
  };
  last7Days: DailySales[];
  salesByDay: DailySales[];
  salesByHour: { hour: number; total: number; count: number }[];
  paymentMethods: { method: PaymentMethod; count: number; total: number; percentage: number }[];
  topProducts: { productId: string; name: string; quantity: number; revenue: number }[];
  attention: {
    productId: string;
    name: string;
    currentStock: number;
    minimumStock: number;
    unit: ProductUnit;
    situation: StockSituation;
  }[];
  advanced?: {
    grossProfit?: number;
    marginPercent?: number;
    canceledCount: number;
    discountTotal: number;
    salesByHour: { hour: number; total: number; count: number }[];
  };
}

export interface NotificationItem {
  type: string;
  severity: 'danger' | 'warning' | 'info';
  title: string;
  message: string;
  link: string;
}

export interface SearchHit {
  id: string;
  title: string;
  subtitle?: string;
}

export interface SearchResponse {
  products: SearchHit[];
  customers: SearchHit[];
  sales: SearchHit[];
}

// ---------- Relatórios ----------

export type ReportValueType = 'TEXT' | 'MONEY' | 'NUMBER' | 'INTEGER' | 'DATE' | 'DATETIME' | 'PERCENT';

export interface ReportInfo {
  type: string;
  title: string;
  advanced: boolean;
  availableInPlan: boolean;
  allowed: boolean;
}

export interface ReportResult {
  type: string;
  title: string;
  from: string;
  to: string;
  summary: { label: string; value: number | string; type: ReportValueType }[];
  columns: { key: string; label: string; type: ReportValueType }[];
  rows: Record<string, unknown>[];
  chart: { label: string; value: number }[];
}

// ---------- Importação ----------

export type ImportEntityType = 'PRODUCTS' | 'CATEGORIES' | 'STOCK' | 'CUSTOMERS' | 'SUPPLIERS';

export interface ImportField {
  key: string;
  label: string;
  required: boolean;
}

export interface ImportUpload {
  jobId: string;
  entityType: ImportEntityType;
  fileName: string;
  totalRows: number;
  columns: string[];
  fields: ImportField[];
  suggestedMapping: Record<string, number>;
  sample: string[][];
}

export interface ImportRowPreview {
  rowNumber: number;
  values: Record<string, string | null>;
  errors: string[];
  status: string;
}

export interface ImportValidation {
  jobId: string;
  totalRows: number;
  validRows: number;
  invalidRows: number;
  preview: ImportRowPreview[];
}

export interface ImportReport {
  jobId: string;
  entityType: ImportEntityType;
  fileName: string;
  status: string;
  totalRows: number;
  validRows: number;
  invalidRows: number;
  createdCount: number;
  updatedCount: number;
  skippedCount: number;
  createdAt: string;
  completedAt?: string;
  rejectedRows: ImportRowPreview[];
}

export interface ImportJobSummary {
  id: string;
  entityType: ImportEntityType;
  fileName: string;
  status: string;
  totalRows: number;
  createdCount: number;
  updatedCount: number;
  skippedCount: number;
  createdAt: string;
}

// ---------- Usuários e permissões ----------

export interface UserSummary {
  id: string;
  name: string;
  email: string;
  phone?: string;
  roleId: string;
  roleName: string;
  roleCode?: string;
  status: 'ACTIVE' | 'INACTIVE';
  lastLoginAt?: string;
  createdAt: string;
}

export type PermissionEffect = 'ALLOW' | 'DENY';

export interface UserDetail {
  user: UserSummary;
  overrides: Record<string, PermissionEffect>;
  effectivePermissions: string[];
}

export interface Role {
  id: string;
  code?: string;
  name: string;
  description?: string;
  systemRole: boolean;
  permissions: string[];
  userCount: number;
}

export interface PermissionInfo {
  code: string;
  module: string;
  moduleLabel: string;
  action: 'VIEW' | 'CREATE' | 'UPDATE' | 'DELETE' | 'SPECIAL';
  label: string;
  feature?: string;
  available: boolean;
}

// ---------- Auditoria ----------

export interface AuditEntry {
  id: string;
  tenantId?: string;
  actorType: string;
  actorId?: string;
  actorName?: string;
  action: string;
  entity?: string;
  entityId?: string;
  ip?: string;
  endpoint?: string;
  metadata?: string;
  createdAt: string;
}

// ---------- Configurações / assinatura ----------

export interface TenantSettings {
  companyName: string;
  tradeName?: string;
  document?: string;
  phone?: string;
  email?: string;
  address?: string;
  timezone: string;
  currency: string;
  primaryColor?: string;
  secondaryColor?: string;
  allowNegativeStock: boolean;
  logoDataUrl?: string;
  updatedAt: string;
}

export interface TenantSubscriptionView {
  planCode: string;
  planName: string;
  status: string;
  billingCycle: string;
  trialEndDate?: string;
  currentPeriodEnd?: string;
  features: string[];
  limits: Record<string, number>;
  usage: {
    activeUsers: number;
    products: number;
    salesThisMonth: number;
    maxUsers: number;
    maxProducts: number;
    maxMonthlySales: number;
  };
}

export interface CatalogPlan {
  code: string;
  name: string;
  description?: string;
  monthlyPrice?: number;
  annualPrice?: number;
  displayOrder: number;
  features: { code: string; name: string }[];
}
