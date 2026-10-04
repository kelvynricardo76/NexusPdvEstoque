package com.nexus.pdv.plan.domain;

/**
 * Funcionalidades que o backend sabe aplicar. O catálogo (nome/descrição) e a composição dos
 * planos ficam no banco e são editáveis pelo Super Admin; o código é o contrato.
 */
public enum FeatureCode {
    PDV,
    SALES,
    PRODUCTS,
    CATEGORIES,
    STOCK,
    CUSTOMERS,
    SUPPLIERS,
    FINANCIAL,
    REPORTS,
    ADVANCED_REPORTS,
    DATA_IMPORT,
    CSV_EXPORT,
    PDF_EXPORT,
    ADVANCED_DASHBOARD,
    MULTI_BRANCH,
    BATCH_CONTROL,
    EXPIRATION_CONTROL,
    SCALE_INTEGRATION
}
