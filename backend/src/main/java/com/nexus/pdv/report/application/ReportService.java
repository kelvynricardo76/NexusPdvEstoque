package com.nexus.pdv.report.application;

import com.nexus.pdv.dashboard.api.DashboardDtos.DailySales;
import com.nexus.pdv.dashboard.application.SalesAggregation;
import com.nexus.pdv.financial.domain.FinancialEntry;
import com.nexus.pdv.financial.infrastructure.FinancialEntryRepository;
import com.nexus.pdv.permission.domain.Permission;
import com.nexus.pdv.plan.domain.LimitCode;
import com.nexus.pdv.product.domain.Product;
import com.nexus.pdv.product.domain.StockSituation;
import com.nexus.pdv.product.infrastructure.ProductRepository;
import com.nexus.pdv.report.domain.ReportResult;
import com.nexus.pdv.report.domain.ReportResult.ChartPoint;
import com.nexus.pdv.report.domain.ReportResult.Column;
import com.nexus.pdv.report.domain.ReportResult.Metric;
import com.nexus.pdv.report.domain.ReportResult.ValueType;
import com.nexus.pdv.report.domain.ReportType;
import com.nexus.pdv.sale.domain.Payment;
import com.nexus.pdv.sale.domain.Sale;
import com.nexus.pdv.sale.infrastructure.SaleRepository;
import com.nexus.pdv.shared.access.AccessContext;
import com.nexus.pdv.shared.access.AccessContextService;
import com.nexus.pdv.shared.error.BusinessException;
import com.nexus.pdv.shared.error.ErrorCode;
import com.nexus.pdv.shared.time.Period;
import com.nexus.pdv.stock.application.StockService;
import com.nexus.pdv.stock.domain.StockMovement;
import com.nexus.pdv.stock.infrastructure.StockMovementRepository;
import com.nexus.pdv.entitlement.EntitlementService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Geração de relatórios. Aplica: permissão do tipo de relatório, features exigidas,
 * limite de histórico do plano (REPORT_HISTORY_DAYS) e visibilidade de custo (PRODUCT_COST_VIEW).
 */
@Service
public class ReportService {

    private static final int MAX_ROWS = 5000;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM");

    private final SaleRepository saleRepository;
    private final ProductRepository productRepository;
    private final StockMovementRepository movementRepository;
    private final FinancialEntryRepository financialRepository;
    private final AccessContextService accessContextService;
    private final Clock clock;

    public ReportService(SaleRepository saleRepository, ProductRepository productRepository,
            StockMovementRepository movementRepository, FinancialEntryRepository financialRepository,
            AccessContextService accessContextService, Clock clock) {
        this.saleRepository = saleRepository;
        this.productRepository = productRepository;
        this.movementRepository = movementRepository;
        this.financialRepository = financialRepository;
        this.accessContextService = accessContextService;
        this.clock = clock;
    }

    /** Verifica permissão, features e histórico permitido pelo plano. */
    public void authorize(ReportType type, Period period) {
        AccessContext context = accessContextService.current();
        context.require(type.permission());
        if (type.extraFeature() != null) {
            context.requireFeature(type.extraFeature());
        }
        if (type == ReportType.FINANCIAL) {
            context.require(Permission.FINANCIAL_READ);
        }
        long historyDays = context.entitlements().limit(LimitCode.REPORT_HISTORY_DAYS);
        if (!LimitCode.isUnlimited(historyDays)) {
            LocalDate earliest = LocalDate.now(clock.withZone(period.zone())).minusDays(historyDays - 1);
            if (period.start().isBefore(earliest)) {
                throw new BusinessException(ErrorCode.PLAN_LIMIT_REACHED,
                        EntitlementService.limitMessage(LimitCode.REPORT_HISTORY_DAYS, historyDays));
            }
        }
    }

    @Transactional(readOnly = true)
    public ReportResult generate(ReportType type, Period period) {
        authorize(type, period);
        boolean costView = accessContextService.current().hasPermission(Permission.PRODUCT_COST_VIEW);
        return switch (type) {
            case SALES -> sales(period);
            case STOCK -> stock(period, false, costView);
            case LOW_STOCK -> stock(period, true, costView);
            case TOP_PRODUCTS -> topProducts(period, costView);
            case MOVEMENTS -> movements(period);
            case CUSTOMERS -> customers(period);
            case FINANCIAL -> financial(period);
        };
    }

    private ReportResult sales(Period period) {
        List<Sale> sales = saleRepository.completedInPeriod(period.startInstant(), period.endExclusive());
        Object[] summary = saleRepository.completedSummary(period.startInstant(), period.endExclusive()).get(0);
        long count = ((Number) summary[0]).longValue();
        BigDecimal total = (BigDecimal) summary[1];
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Sale sale : sales.stream().limit(MAX_ROWS).toList()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("number", sale.getNumber());
            row.put("createdAt", sale.getCreatedAt());
            row.put("customer", sale.getCustomer() == null ? "Consumidor final" : sale.getCustomer().getName());
            row.put("operator", sale.getOperator().getName());
            row.put("payments", sale.getPayments().stream().map(Payment::getMethod).map(Enum::name).distinct()
                    .map(ReportService::paymentLabel).collect(Collectors.joining(", ")));
            row.put("discount", sale.getDiscount());
            row.put("total", sale.getTotal());
            row.put("refunded", sale.getRefundedTotal());
            rows.add(row);
        }
        List<DailySales> byDay = SalesAggregation.byDay(
                saleRepository.completedTotals(period.startInstant(), period.endExclusive()), period);
        return new ReportResult(ReportType.SALES.name(), ReportType.SALES.title(), period.start(), period.end(),
                List.of(
                        new Metric("Vendas", count, ValueType.INTEGER),
                        new Metric("Faturamento", total, ValueType.MONEY),
                        new Metric("Ticket médio", count == 0 ? BigDecimal.ZERO
                                : total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP), ValueType.MONEY),
                        new Metric("Descontos", summary[2], ValueType.MONEY),
                        new Metric("Canceladas", saleRepository.countCanceled(period.startInstant(), period.endExclusive()),
                                ValueType.INTEGER)),
                List.of(
                        new Column("number", "Venda", ValueType.INTEGER),
                        new Column("createdAt", "Data", ValueType.DATETIME),
                        new Column("customer", "Cliente", ValueType.TEXT),
                        new Column("operator", "Operador", ValueType.TEXT),
                        new Column("payments", "Pagamento", ValueType.TEXT),
                        new Column("discount", "Desconto", ValueType.MONEY),
                        new Column("total", "Total", ValueType.MONEY),
                        new Column("refunded", "Devolvido", ValueType.MONEY)),
                rows,
                byDay.stream().map(day -> new ChartPoint(day.date().format(DAY), day.total())).toList());
    }

    private ReportResult stock(Period period, boolean onlyAttention, boolean costView) {
        List<Product> products = new ArrayList<>();
        int page = 0;
        while (products.size() < MAX_ROWS) {
            var chunk = productRepository.search(null, null, Boolean.TRUE, null, PageRequest.of(page++, 500, Sort.by("name")));
            chunk.getContent().stream()
                    .filter(product -> !onlyAttention || product.situation() != StockSituation.NORMAL)
                    .forEach(products::add);
            if (!chunk.hasNext()) {
                break;
            }
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal saleValue = BigDecimal.ZERO;
        BigDecimal costValue = BigDecimal.ZERO;
        for (Product product : products) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", product.getName());
            row.put("sku", product.getSku());
            row.put("category", product.getCategory() == null ? null : product.getCategory().getName());
            row.put("stock", product.getCurrentStock());
            row.put("minimum", product.getMinimumStock());
            row.put("unit", product.getUnit().name());
            row.put("situation", situationLabel(product.situation()));
            BigDecimal positive = product.getCurrentStock().max(BigDecimal.ZERO);
            BigDecimal value = positive.multiply(product.getSalePrice()).setScale(2, RoundingMode.HALF_UP);
            row.put("saleValue", value);
            saleValue = saleValue.add(value);
            if (costView) {
                BigDecimal cost = positive.multiply(product.getCostPrice()).setScale(2, RoundingMode.HALF_UP);
                row.put("costValue", cost);
                costValue = costValue.add(cost);
            }
            rows.add(row);
        }
        List<Column> columns = new ArrayList<>(List.of(
                new Column("name", "Produto", ValueType.TEXT),
                new Column("sku", "SKU", ValueType.TEXT),
                new Column("category", "Categoria", ValueType.TEXT),
                new Column("stock", "Estoque", ValueType.NUMBER),
                new Column("minimum", "Mínimo", ValueType.NUMBER),
                new Column("unit", "Un.", ValueType.TEXT),
                new Column("situation", "Situação", ValueType.TEXT),
                new Column("saleValue", "Valor de venda", ValueType.MONEY)));
        List<Metric> summary = new ArrayList<>(List.of(
                new Metric("Produtos", rows.size(), ValueType.INTEGER),
                new Metric("Estoque baixo", productRepository.countLowStock(), ValueType.INTEGER),
                new Metric("Sem estoque", productRepository.countOutOfStock(), ValueType.INTEGER),
                new Metric("Valor em estoque (venda)", saleValue, ValueType.MONEY)));
        if (costView) {
            columns.add(new Column("costValue", "Valor de custo", ValueType.MONEY));
            summary.add(new Metric("Valor em estoque (custo)", costValue, ValueType.MONEY));
        }
        ReportType type = onlyAttention ? ReportType.LOW_STOCK : ReportType.STOCK;
        LocalDate today = LocalDate.now(clock.withZone(period.zone()));
        return new ReportResult(type.name(), type.title(), today, today, summary, columns, rows, List.of());
    }

    /** Agregações com divisão (receita líquida de devoluções) voltam com escala variável. */
    private static BigDecimal money(Object value) {
        return new BigDecimal(value.toString()).setScale(2, RoundingMode.HALF_UP);
    }

    private ReportResult topProducts(Period period, boolean costView) {
        List<Object[]> data = saleRepository.topProducts(period.startInstant(), period.endExclusive(), PageRequest.of(0, 100));
        BigDecimal revenueTotal = data.stream().map(row -> money(row[3])).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Map<String, Object>> rows = new ArrayList<>();
        int rank = 1;
        for (Object[] item : data) {
            Map<String, Object> row = new LinkedHashMap<>();
            BigDecimal revenue = money(item[3]);
            row.put("rank", rank++);
            row.put("name", item[1]);
            row.put("quantity", item[2]);
            row.put("revenue", revenue);
            row.put("share", revenueTotal.signum() == 0 ? BigDecimal.ZERO
                    : revenue.multiply(BigDecimal.valueOf(100)).divide(revenueTotal, 1, RoundingMode.HALF_UP));
            if (costView) {
                BigDecimal cost = money(item[4]);
                row.put("cost", cost);
                row.put("profit", revenue.subtract(cost));
            }
            rows.add(row);
        }
        List<Column> columns = new ArrayList<>(List.of(
                new Column("rank", "#", ValueType.INTEGER),
                new Column("name", "Produto", ValueType.TEXT),
                new Column("quantity", "Quantidade", ValueType.NUMBER),
                new Column("revenue", "Faturamento", ValueType.MONEY),
                new Column("share", "Participação", ValueType.PERCENT)));
        if (costView) {
            columns.add(new Column("cost", "Custo", ValueType.MONEY));
            columns.add(new Column("profit", "Lucro bruto", ValueType.MONEY));
        }
        return new ReportResult(ReportType.TOP_PRODUCTS.name(), ReportType.TOP_PRODUCTS.title(), period.start(),
                period.end(),
                List.of(new Metric("Produtos vendidos", rows.size(), ValueType.INTEGER),
                        new Metric("Faturamento", revenueTotal, ValueType.MONEY)),
                columns, rows,
                rows.stream().limit(10).map(row -> new ChartPoint((String) row.get("name"), row.get("quantity"))).toList());
    }

    private ReportResult movements(Period period) {
        var page = movementRepository.search(null, null, period.startInstant(), period.endExclusive(),
                PageRequest.of(0, MAX_ROWS));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (StockMovement movement : page.getContent()) {
            var response = StockService.toResponse(movement);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("createdAt", response.createdAt());
            row.put("product", response.productName());
            row.put("type", movementLabel(response.type().name()));
            row.put("quantity", response.quantity());
            row.put("previous", response.previousStock());
            row.put("new", response.newStock());
            row.put("reason", response.reason());
            row.put("user", response.userName());
            rows.add(row);
        }
        return new ReportResult(ReportType.MOVEMENTS.name(), ReportType.MOVEMENTS.title(), period.start(), period.end(),
                List.of(new Metric("Movimentações", page.getTotalElements(), ValueType.INTEGER)),
                List.of(
                        new Column("createdAt", "Data", ValueType.DATETIME),
                        new Column("product", "Produto", ValueType.TEXT),
                        new Column("type", "Tipo", ValueType.TEXT),
                        new Column("quantity", "Qtd.", ValueType.NUMBER),
                        new Column("previous", "Anterior", ValueType.NUMBER),
                        new Column("new", "Novo", ValueType.NUMBER),
                        new Column("reason", "Motivo", ValueType.TEXT),
                        new Column("user", "Usuário", ValueType.TEXT)),
                rows, List.of());
    }

    private ReportResult customers(Period period) {
        List<Object[]> data = saleRepository.customerRanking(period.startInstant(), period.endExclusive(), PageRequest.of(0, 200));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object[] item : data) {
            long purchases = ((Number) item[2]).longValue();
            BigDecimal total = (BigDecimal) item[3];
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", item[1]);
            row.put("purchases", purchases);
            row.put("total", total);
            row.put("average", purchases == 0 ? BigDecimal.ZERO : total.divide(BigDecimal.valueOf(purchases), 2, RoundingMode.HALF_UP));
            row.put("lastPurchase", item[4]);
            rows.add(row);
        }
        return new ReportResult(ReportType.CUSTOMERS.name(), ReportType.CUSTOMERS.title(), period.start(), period.end(),
                List.of(new Metric("Clientes com compras", rows.size(), ValueType.INTEGER)),
                List.of(
                        new Column("name", "Cliente", ValueType.TEXT),
                        new Column("purchases", "Compras", ValueType.INTEGER),
                        new Column("total", "Total", ValueType.MONEY),
                        new Column("average", "Ticket médio", ValueType.MONEY),
                        new Column("lastPurchase", "Última compra", ValueType.DATETIME)),
                rows,
                rows.stream().limit(10).map(row -> new ChartPoint((String) row.get("name"), row.get("total"))).toList());
    }

    private ReportResult financial(Period period) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object[] item : financialRepository.paidByCategory(period.start(), period.end())) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", item[0] == FinancialEntry.Type.RECEIVABLE ? "Receita" : "Despesa");
            row.put("category", item[1]);
            row.put("amount", item[2]);
            rows.add(row);
        }
        BigDecimal revenue = financialRepository.sumPaid(FinancialEntry.Type.RECEIVABLE, period.start(), period.end());
        BigDecimal expenses = financialRepository.sumPaid(FinancialEntry.Type.PAYABLE, period.start(), period.end());
        return new ReportResult(ReportType.FINANCIAL.name(), ReportType.FINANCIAL.title(), period.start(), period.end(),
                List.of(new Metric("Receitas", revenue, ValueType.MONEY),
                        new Metric("Despesas", expenses, ValueType.MONEY),
                        new Metric("Resultado", revenue.subtract(expenses), ValueType.MONEY)),
                List.of(new Column("type", "Tipo", ValueType.TEXT),
                        new Column("category", "Categoria", ValueType.TEXT),
                        new Column("amount", "Valor pago", ValueType.MONEY)),
                rows, List.of(new ChartPoint("Receitas", revenue), new ChartPoint("Despesas", expenses)));
    }

    static String paymentLabel(String method) {
        return switch (method) {
            case "PIX" -> "PIX";
            case "CASH" -> "Dinheiro";
            case "CREDIT_CARD" -> "Crédito";
            case "DEBIT_CARD" -> "Débito";
            default -> "Outros";
        };
    }

    static String situationLabel(StockSituation situation) {
        return switch (situation) {
            case NORMAL -> "Normal";
            case LOW_STOCK -> "Baixo";
            case OUT_OF_STOCK -> "Sem estoque";
        };
    }

    static String movementLabel(String type) {
        return switch (type) {
            case "INITIAL" -> "Estoque inicial";
            case "ENTRY" -> "Entrada";
            case "SALE" -> "Venda";
            case "RETURN" -> "Devolução";
            case "POSITIVE_ADJUSTMENT" -> "Ajuste (+)";
            case "NEGATIVE_ADJUSTMENT" -> "Ajuste (-)";
            case "SALE_CANCELLATION" -> "Cancelamento de venda";
            default -> type;
        };
    }

}
