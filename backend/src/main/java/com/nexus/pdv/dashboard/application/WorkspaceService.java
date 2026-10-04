package com.nexus.pdv.dashboard.application;

import com.nexus.pdv.customer.infrastructure.CustomerRepository;
import com.nexus.pdv.dashboard.api.DashboardDtos.NotificationItem;
import com.nexus.pdv.dashboard.api.DashboardDtos.SearchHit;
import com.nexus.pdv.dashboard.api.DashboardDtos.SearchResponse;
import com.nexus.pdv.financial.domain.FinancialEntry;
import com.nexus.pdv.financial.infrastructure.FinancialEntryRepository;
import com.nexus.pdv.permission.domain.Permission;
import com.nexus.pdv.product.infrastructure.ProductRepository;
import com.nexus.pdv.sale.infrastructure.SaleRepository;
import com.nexus.pdv.shared.access.AccessContext;
import com.nexus.pdv.shared.access.AccessContextService;
import com.nexus.pdv.shared.text.Documents;
import com.nexus.pdv.shared.text.Texts;
import com.nexus.pdv.subscription.domain.SubscriptionStatus;
import com.nexus.pdv.subscription.domain.TenantSubscription;
import com.nexus.pdv.tenant.application.TenantSettingsService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Notificações do header e busca global — ambas respeitam as permissões do usuário. */
@Service
public class WorkspaceService {

    private final AccessContextService accessContextService;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final SaleRepository saleRepository;
    private final FinancialEntryRepository financialRepository;
    private final TenantSettingsService settingsService;
    private final Clock clock;

    public WorkspaceService(AccessContextService accessContextService, ProductRepository productRepository,
            CustomerRepository customerRepository, SaleRepository saleRepository,
            FinancialEntryRepository financialRepository, TenantSettingsService settingsService, Clock clock) {
        this.accessContextService = accessContextService;
        this.productRepository = productRepository;
        this.customerRepository = customerRepository;
        this.saleRepository = saleRepository;
        this.financialRepository = financialRepository;
        this.settingsService = settingsService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<NotificationItem> notifications() {
        AccessContext context = accessContextService.current();
        LocalDate today = LocalDate.now(clock.withZone(settingsService.zone()));
        List<NotificationItem> items = new ArrayList<>();

        if (context.hasPermission(Permission.STOCK_READ)) {
            long out = productRepository.countOutOfStock();
            long low = productRepository.countLowStock();
            if (out > 0) {
                items.add(new NotificationItem("STOCK_OUT", "danger", "Produtos sem estoque",
                        out + " produto(s) sem estoque.", "/estoque?situacao=OUT_OF_STOCK"));
            }
            if (low > 0) {
                items.add(new NotificationItem("STOCK_LOW", "warning", "Estoque baixo",
                        low + " produto(s) abaixo do estoque mínimo.", "/estoque?situacao=LOW_STOCK"));
            }
        }
        if (context.hasPermission(Permission.FINANCIAL_READ)) {
            long overduePayables = ((Number) financialRepository.overdueTotals(FinancialEntry.Type.PAYABLE, today).get(0)[1]).longValue();
            long overdueReceivables = ((Number) financialRepository.overdueTotals(FinancialEntry.Type.RECEIVABLE, today).get(0)[1]).longValue();
            if (overduePayables > 0) {
                items.add(new NotificationItem("PAYABLE_OVERDUE", "danger", "Contas a pagar vencidas",
                        overduePayables + " conta(s) vencida(s).", "/financeiro?status=OVERDUE&tipo=PAYABLE"));
            }
            if (overdueReceivables > 0) {
                items.add(new NotificationItem("RECEIVABLE_OVERDUE", "warning", "Recebimentos atrasados",
                        overdueReceivables + " conta(s) a receber vencida(s).", "/financeiro?status=OVERDUE&tipo=RECEIVABLE"));
            }
        }
        if (context.tenantAdmin()) {
            TenantSubscription subscription = context.subscription();
            if (subscription != null && subscription.getStatus() == SubscriptionStatus.TRIAL
                    && subscription.getTrialEndDate() != null) {
                long days = ChronoUnit.DAYS.between(today, subscription.getTrialEndDate());
                if (days <= 7) {
                    items.add(new NotificationItem("TRIAL_ENDING", "info", "Período de teste",
                            days >= 0 ? "Seu teste termina em " + days + " dia(s)." : "Seu período de teste terminou.",
                            "/configuracoes/assinatura"));
                }
            }
            if (subscription != null && subscription.getStatus() == SubscriptionStatus.PAST_DUE) {
                items.add(new NotificationItem("PAST_DUE", "danger", "Assinatura em atraso",
                        "Regularize o pagamento para evitar a suspensão.", "/configuracoes/assinatura"));
            }
        }
        return items;
    }

    @Transactional(readOnly = true)
    public SearchResponse search(String q) {
        AccessContext context = accessContextService.current();
        String term = Texts.searchTerm(q);
        if (term == null || term.length() < 2) {
            return new SearchResponse(List.of(), List.of(), List.of());
        }
        PageRequest top5 = PageRequest.of(0, 5, Sort.by("name"));

        List<SearchHit> products = context.hasPermission(Permission.PRODUCT_READ) || context.hasPermission(Permission.PDV_ACCESS)
                ? productRepository.search(term, null, null, null, top5).map(p -> new SearchHit(p.getId(), p.getName(),
                        p.getSku() != null ? "SKU " + p.getSku() : p.getBarcode())).getContent()
                : List.of();
        List<SearchHit> customers = context.hasPermission(Permission.CUSTOMER_READ)
                ? customerRepository.search(term, null, top5).map(c -> new SearchHit(c.getId(), c.getName(),
                        c.getPhone() != null ? c.getPhone() : Documents.mask(c.getDocument()))).getContent()
                : List.of();
        List<SearchHit> sales = List.of();
        if (context.hasPermission(Permission.SALE_READ)) {
            String digits = term.replace("#", "");
            if (digits.matches("\\d{1,12}")) {
                sales = saleRepository.findByNumber(Long.parseLong(digits))
                        .map(s -> List.of(new SearchHit(s.getId(), "Venda #" + s.getNumber(),
                                s.getStatus().name())))
                        .orElse(List.of());
            }
        }
        return new SearchResponse(products, customers, sales);
    }
}
