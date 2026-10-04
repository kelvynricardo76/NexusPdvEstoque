package com.nexus.pdv.permission.domain;

/** Agrupamento das permissões na matriz de acesso. */
public enum PermissionModule {
    DASHBOARD("Dashboard"),
    PDV("PDV"),
    SALES("Vendas"),
    PRODUCTS("Produtos"),
    CATEGORIES("Categorias"),
    STOCK("Estoque"),
    CUSTOMERS("Clientes"),
    SUPPLIERS("Fornecedores"),
    FINANCIAL("Financeiro"),
    REPORTS("Relatórios"),
    IMPORT("Importação"),
    USERS("Usuários"),
    AUDIT("Auditoria"),
    SETTINGS("Configurações");

    private final String label;

    PermissionModule(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
