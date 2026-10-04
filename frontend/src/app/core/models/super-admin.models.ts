/** Contratos do painel Super Admin. Limites: -1 = ilimitado. */

export type EffectiveTenantStatus = 'TRIAL' | 'ACTIVE' | 'PAST_DUE' | 'SUSPENDED' | 'CANCELED';

export interface TenantSummary {
  id: string;
  name: string;
  tradeName?: string;
  document?: string;
  planCode?: string;
  planName?: string;
  status: EffectiveTenantStatus;
  subscriptionStatus?: string;
  activeUsers: number;
  createdAt: string;
}

export interface SubscriptionView {
  id: string;
  tenantId: string;
  tenantName?: string;
  planCode: string;
  planName: string;
  status: string;
  billingCycle: 'MONTHLY' | 'ANNUAL';
  startDate: string;
  trialEndDate?: string;
  currentPeriodStart?: string;
  currentPeriodEnd?: string;
  cancelAtPeriodEnd: boolean;
  billingProvider: string;
  monthlyRecurringRevenue: number;
}

export interface FeatureEntry {
  code: string;
  inPlan: boolean;
  override?: boolean | null;
  effective: boolean;
}

export interface LimitEntry {
  code: string;
  name: string;
  defaultValue: number;
  planValue?: number | null;
  override?: number | null;
  effective: number;
}

export interface TenantDetail {
  id: string;
  name: string;
  tradeName?: string;
  document?: string;
  email?: string;
  phone?: string;
  administrativeStatus: 'ACTIVE' | 'SUSPENDED' | 'CANCELED';
  statusReason?: string;
  status: EffectiveTenantStatus;
  operable: boolean;
  createdAt: string;
  subscription?: SubscriptionView;
  entitlements: { features: FeatureEntry[]; limits: LimitEntry[] };
  usage: { activeUsers: number; products: number; salesThisMonth: number };
}

export interface TenantUserView {
  id: string;
  name: string;
  email: string;
  role: string;
  status: string;
  lastLoginAt?: string;
}

export interface PlanView {
  id: string;
  code: string;
  name: string;
  description?: string;
  monthlyPrice: number;
  annualPrice: number;
  active: boolean;
  displayOrder: number;
  features: string[];
  limits: Record<string, number>;
  tenantCount: number;
}

export interface PlanRequest {
  code: string;
  name: string;
  description?: string | null;
  monthlyPrice: number;
  annualPrice: number;
  active: boolean;
  displayOrder: number;
  features: string[];
  limits: Record<string, number>;
}

export interface FeatureView {
  code: string;
  name: string;
  description?: string;
  displayOrder: number;
  plans: string[];
}

export interface LimitView {
  code: string;
  name: string;
  description?: string;
  unit?: string;
  defaultValue: number;
  displayOrder: number;
}

export interface InvoiceView {
  id: string;
  tenantId: string;
  tenantName?: string;
  description: string;
  amount: number;
  dueDate: string;
  periodStart?: string;
  periodEnd?: string;
  status: 'PENDING' | 'PAID' | 'OVERDUE' | 'CANCELED';
  paidAt?: string;
  billingProvider: string;
  createdAt: string;
}

export interface PlatformAdminView {
  id: string;
  name: string;
  email: string;
  status: 'ACTIVE' | 'INACTIVE';
  lastLoginAt?: string;
  createdAt: string;
}

export interface PlatformDashboard {
  totalTenants: number;
  activeTenants: number;
  trialTenants: number;
  pastDueTenants: number;
  suspendedTenants: number;
  canceledTenants: number;
  newTenantsLast30Days: number;
  monthlyRecurringRevenue: number;
  planDistribution: { planCode: string; planName: string; tenants: number }[];
  newTenantsByDay: { date: string; count: number }[];
}

export interface PlatformSettings {
  defaultTrialDays: number;
  pastDueGraceDays: number;
}
