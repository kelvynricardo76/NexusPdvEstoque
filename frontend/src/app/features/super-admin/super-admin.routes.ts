import { Routes } from '@angular/router';

export const SUPER_ADMIN_ROUTES: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  {
    path: 'dashboard',
    title: 'Dashboard • Super Admin',
    loadComponent: () => import('./dashboard/sa-dashboard.page').then((m) => m.SaDashboardPage),
  },
  {
    path: 'empresas',
    title: 'Empresas • Super Admin',
    loadComponent: () => import('./tenants/sa-tenants.page').then((m) => m.SaTenantsPage),
  },
  {
    path: 'empresas/nova',
    title: 'Nova empresa • Super Admin',
    loadComponent: () => import('./tenants/sa-tenant-create.page').then((m) => m.SaTenantCreatePage),
  },
  {
    path: 'empresas/:id',
    title: 'Empresa • Super Admin',
    loadComponent: () => import('./tenants/sa-tenant-detail.page').then((m) => m.SaTenantDetailPage),
  },
  {
    path: 'planos',
    title: 'Planos • Super Admin',
    loadComponent: () => import('./plans/sa-plans.page').then((m) => m.SaPlansPage),
  },
  {
    path: 'planos/novo',
    title: 'Novo plano • Super Admin',
    loadComponent: () => import('./plans/sa-plan-editor.page').then((m) => m.SaPlanEditorPage),
  },
  {
    path: 'planos/:id',
    title: 'Editar plano • Super Admin',
    loadComponent: () => import('./plans/sa-plan-editor.page').then((m) => m.SaPlanEditorPage),
  },
  {
    path: 'funcionalidades',
    title: 'Funcionalidades • Super Admin',
    loadComponent: () => import('./catalog/sa-catalog.page').then((m) => m.SaCatalogPage),
  },
  {
    path: 'assinaturas',
    title: 'Assinaturas • Super Admin',
    loadComponent: () => import('./subscriptions/sa-subscriptions.page').then((m) => m.SaSubscriptionsPage),
  },
  {
    path: 'cobrancas',
    title: 'Cobranças • Super Admin',
    loadComponent: () => import('./billing/sa-billing.page').then((m) => m.SaBillingPage),
  },
  {
    path: 'administradores',
    title: 'Administradores • Super Admin',
    loadComponent: () => import('./admins/sa-admins.page').then((m) => m.SaAdminsPage),
  },
  {
    path: 'auditoria',
    title: 'Auditoria • Super Admin',
    loadComponent: () => import('./audit/sa-audit.page').then((m) => m.SaAuditPage),
  },
  {
    path: 'configuracoes',
    title: 'Configurações • Super Admin',
    loadComponent: () => import('./settings/sa-settings.page').then((m) => m.SaSettingsPage),
  },
];
