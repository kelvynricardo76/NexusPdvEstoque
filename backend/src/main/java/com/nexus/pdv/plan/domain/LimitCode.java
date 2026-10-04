package com.nexus.pdv.plan.domain;

/**
 * Limites quantitativos aplicáveis a um tenant. Valores ficam no banco (plano, override ou padrão).
 * O valor {@link #UNLIMITED} significa sem limite.
 */
public enum LimitCode {
    MAX_USERS,
    MAX_PRODUCTS,
    MAX_BRANCHES,
    MAX_MONTHLY_SALES,
    REPORT_HISTORY_DAYS,
    STORAGE_LIMIT_MB;

    public static final long UNLIMITED = -1L;

    public static boolean isUnlimited(long value) {
        return value < 0;
    }
}
