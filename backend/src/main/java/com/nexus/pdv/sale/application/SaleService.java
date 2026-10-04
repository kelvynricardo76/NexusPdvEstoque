package com.nexus.pdv.sale.application;

import com.nexus.pdv.audit.application.AuditService;
import com.nexus.pdv.audit.domain.AuditAction;
import com.nexus.pdv.customer.domain.Customer;
import com.nexus.pdv.customer.infrastructure.CustomerRepository;
import com.nexus.pdv.entitlement.EntitlementService;
import com.nexus.pdv.permission.domain.Permission;
import com.nexus.pdv.plan.domain.LimitCode;
import com.nexus.pdv.product.application.ProductService;
import com.nexus.pdv.product.domain.Product;
import com.nexus.pdv.product.infrastructure.ProductRepository;
import com.nexus.pdv.sale.api.SaleDtos.FinalizeSaleRequest;
import com.nexus.pdv.sale.api.SaleDtos.ItemRequest;
import com.nexus.pdv.sale.api.SaleDtos.PaymentRequest;
import com.nexus.pdv.sale.api.SaleDtos.PaymentResponse;
import com.nexus.pdv.sale.api.SaleDtos.ReceiptResponse;
import com.nexus.pdv.sale.api.SaleDtos.ReturnItemRequest;
import com.nexus.pdv.sale.api.SaleDtos.ReturnSaleRequest;
import com.nexus.pdv.sale.api.SaleDtos.SaleItemResponse;
import com.nexus.pdv.sale.api.SaleDtos.SaleResponse;
import com.nexus.pdv.sale.api.SaleDtos.SaleReturnItemResponse;
import com.nexus.pdv.sale.api.SaleDtos.SaleReturnResponse;
import com.nexus.pdv.sale.api.SaleDtos.SaleSummary;
import com.nexus.pdv.sale.domain.Payment;
import com.nexus.pdv.sale.domain.PaymentMethod;
import com.nexus.pdv.sale.domain.Sale;
import com.nexus.pdv.sale.domain.SaleItem;
import com.nexus.pdv.sale.domain.SaleReturn;
import com.nexus.pdv.sale.domain.SaleReturnItem;
import com.nexus.pdv.sale.domain.SaleStatus;
import com.nexus.pdv.sale.infrastructure.SaleRepository;
import com.nexus.pdv.sale.infrastructure.SaleReturnRepository;
import com.nexus.pdv.shared.access.AccessContext;
import com.nexus.pdv.shared.access.AccessContextService;
import com.nexus.pdv.shared.error.BusinessException;
import com.nexus.pdv.shared.error.ErrorCode;
import com.nexus.pdv.shared.text.Texts;
import com.nexus.pdv.shared.time.Period;
import com.nexus.pdv.stock.application.StockService;
import com.nexus.pdv.stock.domain.StockMovementType;
import com.nexus.pdv.tenant.application.TenantCounterService;
import com.nexus.pdv.tenant.domain.TenantSettings;
import com.nexus.pdv.tenant.infrastructure.TenantRepository;
import com.nexus.pdv.tenant.infrastructure.TenantSettingsRepository;
import com.nexus.pdv.user.domain.User;
import com.nexus.pdv.user.infrastructure.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Vendas do PDV.
 *
 * <p>Finalização em UMA transação: cria venda, itens e pagamentos, valida e baixa estoque,
 * gera movimentos e auditoria — sem persistência parcial. Produtos são travados em ordem de id
 * (evita deadlock e estoque negativo sob concorrência). Com {@code Idempotency-Key}, repetir a
 * mesma requisição devolve a venda já criada em vez de duplicá-la.
 */
@Service
public class SaleService {

    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("^[A-Za-z0-9_-]{8,80}$");
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);

    private final SaleRepository saleRepository;
    private final SaleReturnRepository returnRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final TenantSettingsRepository settingsRepository;
    private final TenantCounterService counterService;
    private final StockService stockService;
    private final EntitlementService entitlementService;
    private final AccessContextService accessContextService;
    private final AuditService auditService;
    private final Clock clock;

    public SaleService(SaleRepository saleRepository, SaleReturnRepository returnRepository,
            ProductRepository productRepository,
            CustomerRepository customerRepository, UserRepository userRepository, TenantRepository tenantRepository,
            TenantSettingsRepository settingsRepository, TenantCounterService counterService, StockService stockService,
            EntitlementService entitlementService, AccessContextService accessContextService, AuditService auditService,
            Clock clock) {
        this.saleRepository = saleRepository;
        this.returnRepository = returnRepository;
        this.productRepository = productRepository;
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.settingsRepository = settingsRepository;
        this.counterService = counterService;
        this.stockService = stockService;
        this.entitlementService = entitlementService;
        this.accessContextService = accessContextService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public SaleResponse finalizeSale(FinalizeSaleRequest request, String idempotencyKey) {
        AccessContext context = accessContextService.current();
        String key = Texts.clean(idempotencyKey);
        if (key != null) {
            if (!IDEMPOTENCY_KEY.matcher(key).matches()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Idempotency-Key inválida.");
            }
            var existing = saleRepository.findByIdempotencyKey(key);
            if (existing.isPresent()) {
                return toResponse(existing.get());
            }
        }

        boolean hasDiscount = positive(request.discount())
                || request.items().stream().anyMatch(item -> positive(item.discount()));
        if (hasDiscount) {
            context.require(Permission.SALE_DISCOUNT);
        }

        assertMonthlySalesLimit(context);

        Map<UUID, Product> products = lockProducts(request.items());
        Customer customer = resolveCustomer(request.customerId());
        User operator = userRepository.findById(context.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));

        // Cálculo com preços do cadastro (nunca do cliente).
        List<SaleItem> items = new ArrayList<>();
        BigDecimal subtotal = ZERO;
        BigDecimal itemDiscounts = ZERO;
        int line = 1;
        for (ItemRequest itemRequest : request.items()) {
            Product product = products.get(itemRequest.productId());
            ProductService.assertQuantityMatchesUnit(product, itemRequest.quantity());
            BigDecimal gross = money(product.getSalePrice().multiply(itemRequest.quantity()));
            BigDecimal discount = money(itemRequest.discount() == null ? ZERO : itemRequest.discount());
            if (discount.compareTo(gross) > 0) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "Desconto maior que o valor do item \"" + product.getName() + "\".");
            }
            items.add(new SaleItem(product, line++, itemRequest.quantity(), product.getSalePrice(), discount,
                    gross.subtract(discount)));
            subtotal = subtotal.add(gross);
            itemDiscounts = itemDiscounts.add(discount);
        }
        BigDecimal saleDiscount = money(request.discount() == null ? ZERO : request.discount());
        BigDecimal afterItems = subtotal.subtract(itemDiscounts);
        if (saleDiscount.compareTo(afterItems) > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Desconto maior que o total da venda.");
        }
        BigDecimal totalDiscount = itemDiscounts.add(saleDiscount);
        BigDecimal total = afterItems.subtract(saleDiscount);
        BigDecimal change = validatePayments(request.payments(), total);

        long number = counterService.next(context.tenantId(), TenantCounterService.SALE_NUMBER);
        Sale sale = new Sale(number, customer, operator, subtotal, totalDiscount, total, change, key);
        items.forEach(sale::addItem);
        request.payments().forEach(payment -> sale.addPayment(new Payment(payment.method(), money(payment.amount()))));
        saleRepository.save(sale);

        boolean allowNegative = stockService.allowNegativeStock();
        for (SaleItem item : sale.getItems()) {
            stockService.apply(item.getProduct(), StockMovementType.SALE, item.getQuantity().negate(), "SALE",
                    sale.getId(), "Venda #" + number, allowNegative);
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("number", number);
        meta.put("total", total);
        meta.put("items", items.size());
        auditService.record(AuditAction.SALE, "Sale", sale.getId(), meta);
        return toResponse(sale);
    }

    /** Cancelamento idempotente: repetir o pedido sobre uma venda já cancelada não altera nada. */
    @Transactional
    public SaleResponse cancel(UUID saleId, String reason) {
        AccessContext context = accessContextService.current();
        Sale sale = saleRepository.lockById(saleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Venda não encontrada."));
        if (sale.isCanceled()) {
            return toResponse(sale);
        }
        if (sale.hasReturns()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE,
                    "Esta venda já tem devolução registrada e não pode ser cancelada. Registre a devolução dos itens restantes.");
        }
        String cleanReason = Texts.clean(reason);
        User user = userRepository.findById(context.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));

        TreeSet<UUID> productIds = sale.getItems().stream().map(item -> item.getProduct().getId())
                .collect(Collectors.toCollection(TreeSet::new));
        Map<UUID, Product> locked = productRepository.lockAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        for (SaleItem item : sale.getItems()) {
            stockService.apply(locked.get(item.getProduct().getId()), StockMovementType.SALE_CANCELLATION,
                    item.getQuantity(), "SALE_CANCELLATION", sale.getId(), "Cancelamento da venda #" + sale.getNumber(),
                    true);
        }
        sale.cancel(cleanReason, user, clock.instant());

        auditService.record(AuditAction.SALE_CANCEL, "Sale", sale.getId(),
                Map.of("number", sale.getNumber(), "total", sale.getTotal(), "reason", cleanReason));
        return toResponse(sale);
    }

    /**
     * Devolução total ou parcial de uma venda concluída, em UMA transação: valida quantidades
     * devolvíveis, calcula o estorno proporcional ao valor efetivamente pago (inclui o rateio do
     * desconto da venda), devolve ao estoque os itens não avariados e audita. Com
     * {@code Idempotency-Key}, repetir o pedido não duplica a devolução.
     */
    @Transactional
    public SaleResponse registerReturn(UUID saleId, ReturnSaleRequest request, String idempotencyKey) {
        AccessContext context = accessContextService.current();
        String key = Texts.clean(idempotencyKey);
        if (key != null) {
            if (!IDEMPOTENCY_KEY.matcher(key).matches()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Idempotency-Key inválida.");
            }
            var existing = returnRepository.findByIdempotencyKey(key);
            if (existing.isPresent()) {
                if (!existing.get().getSale().getId().equals(saleId)) {
                    throw new BusinessException(ErrorCode.CONFLICT, "Idempotency-Key já usada em outra devolução.");
                }
                return toResponse(existing.get().getSale());
            }
        }

        Sale sale = saleRepository.lockById(saleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Venda não encontrada."));
        if (sale.isCanceled()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE, "Venda cancelada não aceita devolução.");
        }

        Map<Integer, SaleItem> byLine = sale.getItems().stream()
                .collect(Collectors.toMap(SaleItem::getLineNumber, Function.identity()));
        Map<SaleItem, BigDecimal> netByItem = allocateNetValue(sale);
        User user = userRepository.findById(context.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
        SaleReturn saleReturn = new SaleReturn(sale, Texts.clean(request.reason()), request.refundMethod(), user, key);

        TreeSet<Integer> seenLines = new TreeSet<>();
        for (ReturnItemRequest itemRequest : request.items()) {
            if (!seenLines.add(itemRequest.lineNumber())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "Item " + itemRequest.lineNumber() + " informado mais de uma vez.");
            }
            SaleItem item = byLine.get(itemRequest.lineNumber());
            if (item == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "Item " + itemRequest.lineNumber() + " não pertence a esta venda.");
            }
            BigDecimal quantity = itemRequest.quantity();
            ProductService.assertQuantityMatchesUnit(item.getProduct(), quantity);
            BigDecimal returnable = item.returnableQuantity();
            if (quantity.compareTo(returnable) > 0) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE, "Quantidade a devolver de \""
                        + item.getDescriptionSnapshot() + "\" maior que a disponível para devolução ("
                        + returnable.stripTrailingZeros().toPlainString() + ").");
            }
            BigDecimal net = netByItem.get(item);
            BigDecimal remainingNet = net.subtract(item.getRefundedAmount());
            // A última devolução do item leva o saldo exato (sem sobra de arredondamento).
            BigDecimal amount = quantity.compareTo(returnable) == 0 ? remainingNet
                    : money(net.multiply(quantity).divide(item.getQuantity(), 6, RoundingMode.HALF_UP)).min(remainingNet);
            boolean restock = itemRequest.restock() == null || itemRequest.restock();
            saleReturn.addItem(new SaleReturnItem(item, quantity, amount, restock));
        }

        returnRepository.save(saleReturn);
        sale.applyReturn(saleReturn);

        TreeSet<UUID> restockIds = saleReturn.getItems().stream().filter(SaleReturnItem::isRestocked)
                .map(returned -> returned.getSaleItem().getProduct().getId())
                .collect(Collectors.toCollection(TreeSet::new));
        if (!restockIds.isEmpty()) {
            Map<UUID, Product> locked = productRepository.lockAllById(restockIds).stream()
                    .collect(Collectors.toMap(Product::getId, Function.identity()));
            for (SaleReturnItem returned : saleReturn.getItems()) {
                if (returned.isRestocked()) {
                    stockService.apply(locked.get(returned.getSaleItem().getProduct().getId()), StockMovementType.RETURN,
                            returned.getQuantity(), "SALE_RETURN", saleReturn.getId(),
                            "Devolução da venda #" + sale.getNumber(), true);
                }
            }
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("number", sale.getNumber());
        meta.put("amount", saleReturn.getTotal());
        meta.put("items", saleReturn.getItems().size());
        meta.put("refundMethod", request.refundMethod().name());
        meta.put("reason", saleReturn.getReason());
        auditService.record(AuditAction.SALE_REFUND, "Sale", sale.getId(), meta);
        return toResponse(sale);
    }

    /**
     * Valor efetivamente pago por item: total do item com o desconto geral da venda rateado
     * proporcionalmente. A última linha absorve a diferença de arredondamento, então a soma
     * é exatamente o total da venda.
     */
    static Map<SaleItem, BigDecimal> allocateNetValue(Sale sale) {
        List<SaleItem> items = sale.getItems();
        BigDecimal itemsTotal = items.stream().map(SaleItem::getTotal).reduce(ZERO, BigDecimal::add);
        Map<SaleItem, BigDecimal> result = new LinkedHashMap<>();
        BigDecimal allocated = ZERO;
        for (int i = 0; i < items.size(); i++) {
            SaleItem item = items.get(i);
            BigDecimal share;
            if (i == items.size() - 1) {
                share = sale.getTotal().subtract(allocated);
            } else if (itemsTotal.signum() == 0) {
                share = ZERO;
            } else {
                share = money(item.getTotal().multiply(sale.getTotal()).divide(itemsTotal, 6, RoundingMode.HALF_UP));
            }
            allocated = allocated.add(share);
            result.put(item, share);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Page<SaleSummary> search(Long number, SaleStatus status, UUID customerId, UUID operatorId, Period period,
            Pageable pageable) {
        return saleRepository.search(number, status, customerId, operatorId, period.startInstant(),
                        period.endExclusive(), pageable)
                .map(sale -> new SaleSummary(sale.getId(), sale.getNumber(), sale.getCreatedAt(),
                        sale.getCustomer() == null ? null : sale.getCustomer().getName(),
                        sale.getOperator().getName(), sale.getTotal(), sale.getStatus().name(),
                        sale.getRefundStatus().name()));
    }

    @Transactional(readOnly = true)
    public SaleResponse get(UUID id) {
        return toResponse(find(id));
    }

    @Transactional(readOnly = true)
    public ReceiptResponse receipt(UUID id) {
        Sale sale = find(id);
        TenantSettings settings = settingsRepository.findById(accessContextService.current().tenantId()).orElse(null);
        return new ReceiptResponse(
                settings == null ? null : settings.getCompanyName(),
                settings == null ? null : settings.getTradeName(),
                settings == null ? null : settings.getDocument(),
                settings == null ? null : settings.getAddress(),
                settings == null ? null : settings.getPhone(),
                settings == null ? null : settings.getLogoDataUrl(),
                settings == null ? null : settings.getPrimaryColor(),
                toResponse(sale));
    }

    private void assertMonthlySalesLimit(AccessContext context) {
        long max = context.entitlements().limit(LimitCode.MAX_MONTHLY_SALES);
        if (LimitCode.isUnlimited(max)) {
            return;
        }
        tenantRepository.lockById(context.tenantId());
        TenantSettings settings = settingsRepository.findById(context.tenantId()).orElse(null);
        var zone = java.time.ZoneId.of(settings == null ? TenantSettings.DEFAULT_TIMEZONE : settings.getTimezone());
        Instant monthStart = LocalDate.now(clock.withZone(zone)).withDayOfMonth(1).atStartOfDay(zone).toInstant();
        entitlementService.assertWithinLimit(context.tenantId(), LimitCode.MAX_MONTHLY_SALES,
                saleRepository.countByCreatedAtGreaterThanEqual(monthStart), 1);
    }

    private Map<UUID, Product> lockProducts(List<ItemRequest> items) {
        TreeSet<UUID> ids = items.stream().map(ItemRequest::productId).collect(Collectors.toCollection(TreeSet::new));
        Map<UUID, Product> products = productRepository.lockAllById(ids).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        for (UUID id : ids) {
            Product product = products.get(id);
            if (product == null) {
                // Inclui produtos de outro tenant: invisíveis pelo filtro de tenant.
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Produto não encontrado.");
            }
            if (!product.isActive()) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE, "O produto \"" + product.getName() + "\" está inativo.");
            }
        }
        return products;
    }

    private Customer resolveCustomer(UUID customerId) {
        if (customerId == null) {
            return null;
        }
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Cliente não encontrado."));
        if (!customer.isActive()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE, "Cliente inativo.");
        }
        return customer;
    }

    /**
     * Soma dos pagamentos deve cobrir o total. Excedente só é aceito como troco em dinheiro
     * (e nunca maior que o valor pago em dinheiro).
     *
     * @return valor do troco
     */
    private static BigDecimal validatePayments(List<PaymentRequest> payments, BigDecimal total) {
        BigDecimal paid = payments.stream().map(p -> money(p.amount())).reduce(ZERO, BigDecimal::add);
        if (paid.compareTo(total) < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "O valor pago é menor que o total da venda.");
        }
        BigDecimal change = paid.subtract(total);
        if (change.signum() > 0) {
            BigDecimal cash = payments.stream().filter(p -> p.method() == PaymentMethod.CASH)
                    .map(p -> money(p.amount())).reduce(ZERO, BigDecimal::add);
            if (cash.compareTo(change) < 0) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "Pagamento excedente só é permitido em dinheiro (troco).");
            }
        }
        return change;
    }

    private Sale find(UUID id) {
        return saleRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Venda não encontrada."));
    }

    private static boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private SaleResponse toResponse(Sale sale) {
        List<SaleItemResponse> items = sale.getItems().stream()
                .map(item -> new SaleItemResponse(item.getProduct().getId(), item.getLineNumber(),
                        item.getDescriptionSnapshot(), item.getSkuSnapshot(), item.getQuantity(), item.getUnitPrice(),
                        item.getDiscount(), item.getTotal(), item.getReturnedQuantity()))
                .toList();
        List<SaleReturnResponse> returns = !sale.hasReturns() ? List.of()
                : returnRepository.findBySale(sale.getId()).stream()
                        .map(saleReturn -> new SaleReturnResponse(saleReturn.getId(), saleReturn.getCreatedAt(),
                                saleReturn.getReason(), saleReturn.getRefundMethod(), saleReturn.getTotal(),
                                saleReturn.getUser().getName(),
                                saleReturn.getItems().stream()
                                        .map(returned -> new SaleReturnItemResponse(
                                                returned.getSaleItem().getLineNumber(),
                                                returned.getSaleItem().getDescriptionSnapshot(), returned.getQuantity(),
                                                returned.getAmount(), returned.isRestocked()))
                                        .toList()))
                        .toList();
        List<PaymentResponse> payments = sale.getPayments().stream()
                .map(payment -> new PaymentResponse(payment.getMethod(), payment.getAmount(), payment.getStatus().name()))
                .toList();
        return new SaleResponse(sale.getId(), sale.getNumber(), sale.getCreatedAt(),
                sale.getCustomer() == null ? null : sale.getCustomer().getId(),
                sale.getCustomer() == null ? null : sale.getCustomer().getName(),
                sale.getOperator().getId(), sale.getOperator().getName(), sale.getSubtotal(), sale.getDiscount(),
                sale.getTotal(), sale.getChangeAmount(), sale.getStatus().name(), sale.getCancelReason(),
                sale.getCanceledAt(), sale.getCanceledBy() == null ? null : sale.getCanceledBy().getName(),
                sale.getRefundStatus().name(), sale.getRefundedTotal(), items, payments, returns);
    }
}
