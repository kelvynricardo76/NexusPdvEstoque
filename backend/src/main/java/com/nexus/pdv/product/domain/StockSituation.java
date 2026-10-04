package com.nexus.pdv.product.domain;

import java.math.BigDecimal;

public enum StockSituation {
    NORMAL,
    LOW_STOCK,
    OUT_OF_STOCK;

    public static StockSituation of(BigDecimal current, BigDecimal minimum) {
        if (current.signum() <= 0) {
            return OUT_OF_STOCK;
        }
        if (current.compareTo(minimum) <= 0) {
            return LOW_STOCK;
        }
        return NORMAL;
    }
}
