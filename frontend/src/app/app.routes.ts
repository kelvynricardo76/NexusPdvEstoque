import { Routes } from '@angular/router';
import {
  accessGuard,
  AccessData,
  blockedGuard,
  guestGuard,
  landingGuard,
  platformAuthGuard,
  platformGuestGuard,
  tenantAuthGuard,
} from './core/auth/guards';

const access = (data: AccessData) => ({ access: data });

export const routes: Routes = [
  // ---------- Acesso ----------
  {
    path: 'login',
    title: 'Entrar • Nexus PDV & Estoque',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/login/login.page').then((m) => m.LoginPage),
  },
  {
    path: 'esqueci-senha',
    title: 'Recuperar acesso • Nexus',
    loadComponent: () => import('./features/auth/password/forgot-password.page').then((m) => m.ForgotPasswordPage),
  },
  {
    path: 'redefinir-senha',
    title: 'Nova senha • Nexus',
    loadComponent: () => import('./features/auth/password/reset-password.page').then((m) => m.ResetPasswordPage),
  },
  {
    path: 'bloqueado',
    title: 'Acesso suspenso • Nexus',
    canActivate: [blockedGuard],
    loadComponent: () => import('./features/auth/blocked/blocked.page').then((m) => m.BlockedPage),
  },

  // ---------- Super Admin (Nexus Development) ----------
  {
    path: 'super-admin/login',
    title: 'Super Admin • Nexus',
    canActivate: [platformGuestGuard],
    data: { platform: true },
    loadComponent: () => import('./features/auth/login/login.page').then((m) => m.LoginPage),
  },
  {
    path: 'super-admin',
    canActivate: [platformAuthGuard],
    loadComponent: () => import('./layout/super-admin-shell/super-admin-shell.component').then((m) => m.SuperAdminShellComponent),
    loadChildren: () => import('./features/super-admin/super-admin.routes').then((m) => m.SUPER_ADMIN_ROUTES),
  },

  // ---------- Aplicação do tenant ----------
  { path: '', pathMatch: 'full', canActivate: [landingGuard], children: [] },
  {
    path: '',
    canActivate: [tenantAuthGuard],
    loadComponent: () => import('./layout/app-shell/app-shell.component').then((m) => m.AppShellComponent),
    children: [
      {
        path: 'inicio',
        title: 'Início • Nexus',
        canActivate: [accessGuard],
        data: access({ permissions: ['DASHBOARD_VIEW'] }),
        loadComponent: () => import('./features/dashboard/dashboard.page').then((m) => m.DashboardPage),
      },
      {
        path: 'pdv',
        title: 'PDV • Nexus',
        canActivate: [accessGuard],
        data: access({ permissions: ['PDV_ACCESS'], feature: 'PDV' }),
        loadComponent: () => import('./features/pdv/pdv.page').then((m) => m.PdvPage),
      },
      {
        path: 'vendas',
        title: 'Vendas • Nexus',
        canActivate: [accessGuard],
        data: access({ permissions: ['SALE_READ'], feature: 'SALES' }),
        loadComponent: () => import('./features/sales/sales-list.page').then((m) => m.SalesListPage),
      },
      {
        path: 'vendas/:id',
        title: 'Venda • Nexus',
        canActivate: [accessGuard],
        data: access({ permissions: ['SALE_READ', 'SALE_CREATE'] }),
        loadComponent: () => import('./features/sales/sale-detail.page').then((m) => m.SaleDetailPage),
      },
      {
        path: 'estoque',
        title: 'Estoque • Nexus',
        canActivate: [accessGuard],
        data: access({ permissions: ['STOCK_READ'], feature: 'STOCK' }),
        loadComponent: () => import('./features/stock/stock.page').then((m) => m.StockPage),
      },
      {
        path: 'estoque/movimentacoes',
        title: 'Movimentações • Nexus',
        canActivate: [accessGuard],
        data: access({ permissions: ['STOCK_MOVEMENT_READ'], feature: 'STOCK' }),
        loadComponent: () => import('./features/stock/stock-movements.page').then((m) => m.StockMovementsPage),
      },
      {
        path: 'produtos',
        title: 'Produtos • Nexus',
        canActivate: [accessGuard],
        data: access({ permissions: ['PRODUCT_READ'], feature: 'PRODUCTS' }),
        loadComponent: () => import('./features/products/products-list.page').then((m) => m.ProductsListPage),
      },
      {
        path: 'produtos/novo',
        title: 'Novo produto • Nexus',
        canActivate: [accessGuard],
        data: access({ permissions: ['PRODUCT_CREATE'], feature: 'PRODUCTS' }),
        loadComponent: () => import('./features/products/product-form.page').then((m) => m.ProductFormPage),
      },
      {
        path: 'produtos/:id',
        title: 'Produto • Nexus',
        canActivate: [accessGuard],
        data: access({ permissions: ['PRODUCT_READ'], feature: 'PRODUCTS' }),
        loadComponent: () => import('./features/products/product-form.page').then((m) => m.ProductFormPage),
      },
      {
        path: 'categorias',
        title: 'Categorias • Nexus',
        canActivate: [accessGuard],
        data: access({ permissions: ['CATEGORY_READ'], feature: 'CATEGORIES' }),
        loadComponent: () => import('./features/categories/categories.page').then((m) => m.CategoriesPage),
      },
      {
        path: 'clientes',
        title: 'Clientes • Nexus',
        canActivate: [accessGuard],
        data: access({ permissions: ['CUSTOMER_READ'], feature: 'CUSTOMERS' }),
        loadComponent: () => import('./features/customers/customers-list.page').then((m) => m.CustomersListPage),
      },
      {
        path: 'clientes/:id',
        title: 'Cliente • Nexus',
        canActivate: [accessGuard],
        data: access({ permissions: ['CUSTOMER_READ'], feature: 'CUSTOMERS' }),
        loadComponent: () => import('./features/customers/customer-detail.page').then((m) => m.CustomerDetailPage),
      },
      {
        path: 'fornecedores',
        title: 'Fornecedores • Nexus',
        canActivate: [accessGuard],
        data: access({ permissions: ['SUPPLIER_READ'], feature: 'SUPPLIERS' }),
        loadComponent: () => import('./features/suppliers/suppliers.page').then((m) => m.SuppliersPage),
      },
      {
        path: 'financeiro',
        title: 'Financeiro • Nexus',
        canActivate: [accessGuard],
        data: access({ permissions: ['FINANCIAL_READ'], feature: 'FINANCIAL' }),
        loadComponent: () => import('./features/financial/financial.page').then((m) => m.FinancialPage),
      },
      {
        path: 'relatorios',
        title: 'Relatórios • Nexus',
        canActivate: [accessGuard],
        data: access({ permissions: ['REPORT_BASIC_READ', 'REPORT_ADVANCED_READ'], feature: 'REPORTS' }),
        loadComponent: () => import('./features/reports/reports.page').then((m) => m.ReportsPage),
      },
      {
        path: 'importacao',
        title: 'Importação • Nexus',
        canActivate: [accessGuard],
        data: access({ permissions: ['IMPORT_EXECUTE'], feature: 'DATA_IMPORT' }),
        loadComponent: () => import('./features/import/import.page').then((m) => m.ImportPage),
      },
      {
        path: 'usuarios',
        title: 'Usuários • Nexus',
        canActivate: [accessGuard],
        data: access({ permissions: ['USER_READ'] }),
        loadComponent: () => import('./features/users/users.page').then((m) => m.UsersPage),
      },
      {
        path: 'usuarios/cargos',
        title: 'Cargos e permissões • Nexus',
        canActivate: [accessGuard],
        data: access({ permissions: ['USER_READ'] }),
        loadComponent: () => import('./features/users/roles.page').then((m) => m.RolesPage),
      },
      {
        path: 'usuarios/:id/permissoes',
        title: 'Permissões • Nexus',
        canActivate: [accessGuard],
        data: access({ permissions: ['USER_PERMISSION_MANAGE'] }),
        loadComponent: () => import('./features/users/user-permissions.page').then((m) => m.UserPermissionsPage),
      },
      {
        path: 'auditoria',
        title: 'Auditoria • Nexus',
        canActivate: [accessGuard],
        data: access({ permissions: ['AUDIT_READ'] }),
        loadComponent: () => import('./features/audit/audit.page').then((m) => m.AuditPage),
      },
      {
        path: 'configuracoes',
        title: 'Configurações • Nexus',
        loadComponent: () => import('./features/settings/settings.page').then((m) => m.SettingsPage),
        children: [
          { path: '', pathMatch: 'full', redirectTo: 'empresa' },
          {
            path: 'empresa',
            loadComponent: () => import('./features/settings/company-settings.component').then((m) => m.CompanySettingsComponent),
          },
          {
            path: 'assinatura',
            canActivate: [accessGuard],
            data: access({ tenantAdmin: true }),
            loadComponent: () => import('./features/settings/subscription-settings.component').then((m) => m.SubscriptionSettingsComponent),
          },
          {
            path: 'conta',
            loadComponent: () => import('./features/settings/account-settings.component').then((m) => m.AccountSettingsComponent),
          },
        ],
      },
      {
        path: 'upgrade',
        title: 'Planos • Nexus',
        loadComponent: () => import('./features/upgrade/upgrade.page').then((m) => m.UpgradePage),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
