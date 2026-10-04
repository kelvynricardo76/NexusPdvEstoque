package com.nexus.pdv.report.domain;

import com.nexus.pdv.permission.domain.Permission;
import com.nexus.pdv.plan.domain.FeatureCode;

/**
 * Relatórios disponíveis. Básicos exigem REPORTS; avançados exigem ADVANCED_REPORTS
 * (via permissão). Alguns exigem ainda a feature do módulo de origem.
 */
public enum ReportType {

    SALES("Vendas por período", Permission.REPORT_BASIC_READ, null),
    STOCK("Posição de estoque", Permission.REPORT_BASIC_READ, null),
    LOW_STOCK("Estoque baixo e sem estoque", Permission.REPORT_BASIC_READ, null),
    TOP_PRODUCTS("Produtos mais vendidos", Permission.REPORT_ADVANCED_READ, null),
    MOVEMENTS("Movimentações de estoque", Permission.REPORT_ADVANCED_READ, null),
    CUSTOMERS("Clientes que mais compram", Permission.REPORT_ADVANCED_READ, FeatureCode.CUSTOMERS),
    FINANCIAL("Financeiro por categoria", Permission.REPORT_ADVANCED_READ, FeatureCode.FINANCIAL);

    private final String title;
    private final Permission permission;
    private final FeatureCode extraFeature;

    ReportType(String title, Permission permission, FeatureCode extraFeature) {
        this.title = title;
        this.permission = permission;
        this.extraFeature = extraFeature;
    }

    public String title() {
        return title;
    }

    public Permission permission() {
        return permission;
    }

    public FeatureCode extraFeature() {
        return extraFeature;
    }

    public boolean advanced() {
        return permission == Permission.REPORT_ADVANCED_READ;
    }
}
