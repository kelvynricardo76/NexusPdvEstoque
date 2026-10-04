package com.nexus.pdv.permission.domain;

import com.nexus.pdv.plan.domain.FeatureCode;

/**
 * Catálogo de permissões granulares (o que o FUNCIONÁRIO pode fazer).
 * Cada permissão só tem efeito se o tenant possuir a feature associada (o que a EMPRESA contratou).
 * Deve permanecer sincronizado com a tabela {@code permissions} (verificado em teste).
 */
public enum Permission {

    DASHBOARD_VIEW(PermissionModule.DASHBOARD, PermissionAction.VIEW, null, "Visualizar dashboard"),

    PDV_ACCESS(PermissionModule.PDV, PermissionAction.VIEW, FeatureCode.PDV, "Acessar o PDV"),

    SALE_READ(PermissionModule.SALES, PermissionAction.VIEW, FeatureCode.SALES, "Visualizar vendas"),
    SALE_CREATE(PermissionModule.SALES, PermissionAction.CREATE, FeatureCode.PDV, "Registrar vendas"),
    SALE_CANCEL(PermissionModule.SALES, PermissionAction.SPECIAL, FeatureCode.SALES, "Cancelar venda"),
    SALE_DISCOUNT(PermissionModule.SALES, PermissionAction.SPECIAL, FeatureCode.SALES, "Aplicar desconto"),
    SALE_REFUND(PermissionModule.SALES, PermissionAction.SPECIAL, FeatureCode.SALES, "Registrar devolução"),

    PRODUCT_READ(PermissionModule.PRODUCTS, PermissionAction.VIEW, FeatureCode.PRODUCTS, "Visualizar produtos"),
    PRODUCT_CREATE(PermissionModule.PRODUCTS, PermissionAction.CREATE, FeatureCode.PRODUCTS, "Cadastrar produtos"),
    PRODUCT_UPDATE(PermissionModule.PRODUCTS, PermissionAction.UPDATE, FeatureCode.PRODUCTS, "Editar produtos"),
    PRODUCT_DISABLE(PermissionModule.PRODUCTS, PermissionAction.DELETE, FeatureCode.PRODUCTS, "Desativar produtos"),
    PRODUCT_COST_VIEW(PermissionModule.PRODUCTS, PermissionAction.SPECIAL, FeatureCode.PRODUCTS, "Visualizar custo"),

    CATEGORY_READ(PermissionModule.CATEGORIES, PermissionAction.VIEW, FeatureCode.CATEGORIES, "Visualizar categorias"),
    CATEGORY_CREATE(PermissionModule.CATEGORIES, PermissionAction.CREATE, FeatureCode.CATEGORIES, "Cadastrar categorias"),
    CATEGORY_UPDATE(PermissionModule.CATEGORIES, PermissionAction.UPDATE, FeatureCode.CATEGORIES, "Editar categorias"),
    CATEGORY_DISABLE(PermissionModule.CATEGORIES, PermissionAction.DELETE, FeatureCode.CATEGORIES, "Desativar categorias"),

    STOCK_READ(PermissionModule.STOCK, PermissionAction.VIEW, FeatureCode.STOCK, "Visualizar estoque"),
    STOCK_ENTRY(PermissionModule.STOCK, PermissionAction.CREATE, FeatureCode.STOCK, "Registrar entrada"),
    STOCK_ADJUST(PermissionModule.STOCK, PermissionAction.SPECIAL, FeatureCode.STOCK, "Ajustar estoque"),
    STOCK_MOVEMENT_READ(PermissionModule.STOCK, PermissionAction.SPECIAL, FeatureCode.STOCK, "Visualizar movimentações"),

    CUSTOMER_READ(PermissionModule.CUSTOMERS, PermissionAction.VIEW, FeatureCode.CUSTOMERS, "Visualizar clientes"),
    CUSTOMER_CREATE(PermissionModule.CUSTOMERS, PermissionAction.CREATE, FeatureCode.CUSTOMERS, "Cadastrar clientes"),
    CUSTOMER_UPDATE(PermissionModule.CUSTOMERS, PermissionAction.UPDATE, FeatureCode.CUSTOMERS, "Editar clientes"),
    CUSTOMER_DISABLE(PermissionModule.CUSTOMERS, PermissionAction.DELETE, FeatureCode.CUSTOMERS, "Desativar clientes"),

    SUPPLIER_READ(PermissionModule.SUPPLIERS, PermissionAction.VIEW, FeatureCode.SUPPLIERS, "Visualizar fornecedores"),
    SUPPLIER_CREATE(PermissionModule.SUPPLIERS, PermissionAction.CREATE, FeatureCode.SUPPLIERS, "Cadastrar fornecedores"),
    SUPPLIER_UPDATE(PermissionModule.SUPPLIERS, PermissionAction.UPDATE, FeatureCode.SUPPLIERS, "Editar fornecedores"),
    SUPPLIER_DISABLE(PermissionModule.SUPPLIERS, PermissionAction.DELETE, FeatureCode.SUPPLIERS, "Desativar fornecedores"),

    FINANCIAL_READ(PermissionModule.FINANCIAL, PermissionAction.VIEW, FeatureCode.FINANCIAL, "Visualizar financeiro"),
    FINANCIAL_CREATE(PermissionModule.FINANCIAL, PermissionAction.CREATE, FeatureCode.FINANCIAL, "Lançar contas"),
    FINANCIAL_UPDATE(PermissionModule.FINANCIAL, PermissionAction.UPDATE, FeatureCode.FINANCIAL, "Editar e baixar contas"),
    FINANCIAL_CANCEL(PermissionModule.FINANCIAL, PermissionAction.DELETE, FeatureCode.FINANCIAL, "Cancelar contas"),

    REPORT_BASIC_READ(PermissionModule.REPORTS, PermissionAction.VIEW, FeatureCode.REPORTS, "Relatórios básicos"),
    REPORT_ADVANCED_READ(PermissionModule.REPORTS, PermissionAction.SPECIAL, FeatureCode.ADVANCED_REPORTS, "Relatórios avançados"),
    REPORT_EXPORT(PermissionModule.REPORTS, PermissionAction.SPECIAL, FeatureCode.REPORTS, "Exportar relatórios"),

    IMPORT_EXECUTE(PermissionModule.IMPORT, PermissionAction.CREATE, FeatureCode.DATA_IMPORT, "Importar dados"),

    USER_READ(PermissionModule.USERS, PermissionAction.VIEW, null, "Visualizar usuários"),
    USER_CREATE(PermissionModule.USERS, PermissionAction.CREATE, null, "Cadastrar usuários"),
    USER_UPDATE(PermissionModule.USERS, PermissionAction.UPDATE, null, "Editar usuários"),
    USER_DISABLE(PermissionModule.USERS, PermissionAction.DELETE, null, "Desativar usuários"),
    USER_PERMISSION_MANAGE(PermissionModule.USERS, PermissionAction.SPECIAL, null, "Gerenciar cargos e permissões"),

    AUDIT_READ(PermissionModule.AUDIT, PermissionAction.VIEW, null, "Visualizar auditoria"),

    SETTINGS_MANAGE(PermissionModule.SETTINGS, PermissionAction.UPDATE, null, "Gerenciar configurações");

    private final PermissionModule module;
    private final PermissionAction action;
    private final FeatureCode feature;
    private final String label;

    Permission(PermissionModule module, PermissionAction action, FeatureCode feature, String label) {
        this.module = module;
        this.action = action;
        this.feature = feature;
        this.label = label;
    }

    public PermissionModule module() {
        return module;
    }

    public PermissionAction action() {
        return action;
    }

    /** Feature exigida do tenant; {@code null} = recurso central sempre disponível. */
    public FeatureCode feature() {
        return feature;
    }

    public String label() {
        return label;
    }
}
