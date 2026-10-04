package com.nexus.pdv.dashboard.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class DashboardDtos {

    private DashboardDtos() {
    }

    public record DashboardResponse(
            LocalDate from,
            LocalDate to,
            Kpis kpis,
            List<DailySales> last7Days,
            List<DailySales> salesByDay,
            List<HourlySales> salesByHour,
            List<PaymentShare> paymentMethods,
            List<TopProduct> topProducts,
            List<AttentionProduct> attention,
            Advanced advanced) {
    }

    /** Variações em % em relação ao período anterior equivalente (nulo sem base de comparação). */
    public record Kpis(
            BigDecimal salesTotal,
            BigDecimal salesTotalVariation,
            long salesCount,
            BigDecimal salesCountVariation,
            BigDecimal averageTicket,
            long productsInStock,
            long lowStock,
            long outOfStock,
            long activeCustomers) {
    }

    public record DailySales(LocalDate date, BigDecimal total, long count) {
    }

    public record PaymentShare(String method, long count, BigDecimal total, BigDecimal percentage) {
    }

    public record TopProduct(UUID productId, String name, BigDecimal quantity, BigDecimal revenue) {
    }

    public record AttentionProduct(UUID productId, String name, BigDecimal currentStock, BigDecimal minimumStock,
            String unit, String situation) {
    }

    /** Disponível com a feature ADVANCED_DASHBOARD. Lucro/margem exigem PRODUCT_COST_VIEW. */
    public record Advanced(BigDecimal grossProfit, BigDecimal marginPercent, long canceledCount,
            BigDecimal discountTotal, List<HourlySales> salesByHour) {
    }

    public record HourlySales(int hour, BigDecimal total, long count) {
    }

    public record NotificationItem(String type, String severity, String title, String message, String link) {
    }

    public record SearchResponse(List<SearchHit> products, List<SearchHit> customers, List<SearchHit> sales) {
    }

    public record SearchHit(UUID id, String title, String subtitle) {
    }
}
