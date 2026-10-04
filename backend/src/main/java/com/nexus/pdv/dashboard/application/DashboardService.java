package com.nexus.pdv.dashboard.application;

import com.nexus.pdv.customer.infrastructure.CustomerRepository;
import com.nexus.pdv.dashboard.api.DashboardDtos.Advanced;
import com.nexus.pdv.dashboard.api.DashboardDtos.AttentionProduct;
import com.nexus.pdv.dashboard.api.DashboardDtos.DashboardResponse;
import com.nexus.pdv.dashboard.api.DashboardDtos.Kpis;
import com.nexus.pdv.dashboard.api.DashboardDtos.PaymentShare;
import com.nexus.pdv.dashboard.api.DashboardDtos.TopProduct;
import com.nexus.pdv.permission.domain.Permission;
import com.nexus.pdv.plan.domain.FeatureCode;
import com.nexus.pdv.product.infrastructure.ProductRepository;
import com.nexus.pdv.sale.infrastructure.SaleRepository;
import com.nexus.pdv.shared.access.AccessContext;
import com.nexus.pdv.shared.access.AccessContextService;
import com.nexus.pdv.shared.time.Period;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final SaleRepository saleRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final AccessContextService accessContextService;
    private final Clock clock;

    public DashboardService(SaleRepository saleRepository, ProductRepository productRepository,
            CustomerRepository customerRepository, AccessContextService accessContextService, Clock clock) {
        this.saleRepository = saleRepository;
        this.productRepository = productRepository;
        this.customerRepository = customerRepository;
        this.accessContextService = accessContextService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard(Period period) {
        AccessContext context = accessContextService.current();

        Object[] current = saleRepository.completedSummary(period.startInstant(), period.endExclusive()).get(0);
        Period previousPeriod = period.previous();
        Object[] previous = saleRepository.completedSummary(previousPeriod.startInstant(), previousPeriod.endExclusive()).get(0);
        long count = ((Number) current[0]).longValue();
        BigDecimal total = (BigDecimal) current[1];
        long previousCount = ((Number) previous[0]).longValue();
        BigDecimal previousTotal = (BigDecimal) previous[1];

        Kpis kpis = new Kpis(
                total,
                variation(total, previousTotal),
                count,
                variation(BigDecimal.valueOf(count), BigDecimal.valueOf(previousCount)),
                count == 0 ? BigDecimal.ZERO : total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP),
                productRepository.countInStock(),
                productRepository.countLowStock(),
                productRepository.countOutOfStock(),
                customerRepository.countByActiveTrue());

        LocalDate today = LocalDate.now(clock.withZone(period.zone()));
        Period last7 = new Period(today.minusDays(6), today, period.zone());
        var last7Rows = saleRepository.completedTotals(last7.startInstant(), last7.endExclusive());
        var periodRows = saleRepository.completedTotals(period.startInstant(), period.endExclusive());

        List<Object[]> paymentRows = saleRepository.paymentBreakdown(period.startInstant(), period.endExclusive());
        BigDecimal paymentsTotal = paymentRows.stream().map(row -> (BigDecimal) row[2]).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<PaymentShare> payments = paymentRows.stream()
                .map(row -> new PaymentShare(row[0].toString(), ((Number) row[1]).longValue(), (BigDecimal) row[2],
                        percent((BigDecimal) row[2], paymentsTotal)))
                .toList();

        List<TopProduct> top = saleRepository.topProducts(period.startInstant(), period.endExclusive(), PageRequest.of(0, 5))
                .stream()
                .map(row -> new TopProduct((UUID) row[0], (String) row[1], (BigDecimal) row[2],
                        new BigDecimal(row[3].toString()).setScale(2, java.math.RoundingMode.HALF_UP)))
                .toList();

        List<AttentionProduct> attention = productRepository.findAttentionNeeded(PageRequest.of(0, 6)).stream()
                .map(product -> new AttentionProduct(product.getId(), product.getName(), product.getCurrentStock(),
                        product.getMinimumStock(), product.getUnit().name(), product.situation().name()))
                .toList();

        Advanced advanced = null;
        if (context.hasFeature(FeatureCode.ADVANCED_DASHBOARD)) {
            boolean costView = context.hasPermission(Permission.PRODUCT_COST_VIEW);
            BigDecimal profit = null;
            BigDecimal margin = null;
            if (costView) {
                BigDecimal cost = saleRepository.costOfGoodsSold(period.startInstant(), period.endExclusive());
                profit = total.subtract(cost).setScale(2, RoundingMode.HALF_UP);
                margin = percent(profit, total);
            }
            advanced = new Advanced(profit, margin,
                    saleRepository.countCanceled(period.startInstant(), period.endExclusive()),
                    (BigDecimal) current[2], SalesAggregation.byHour(periodRows, period));
        }

        return new DashboardResponse(period.start(), period.end(), kpis, SalesAggregation.byDay(last7Rows, last7),
                SalesAggregation.byDay(periodRows, period), SalesAggregation.byHour(periodRows, period), payments, top,
                attention, advanced);
    }

    static BigDecimal variation(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.signum() == 0) {
            return null;
        }
        return current.subtract(previous).multiply(HUNDRED).divide(previous, 1, RoundingMode.HALF_UP);
    }

    static BigDecimal percent(BigDecimal part, BigDecimal whole) {
        if (whole == null || whole.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return part.multiply(HUNDRED).divide(whole, 1, RoundingMode.HALF_UP);
    }
}
