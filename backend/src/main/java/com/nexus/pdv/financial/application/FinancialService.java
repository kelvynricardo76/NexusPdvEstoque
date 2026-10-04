package com.nexus.pdv.financial.application;

import com.nexus.pdv.audit.application.AuditService;
import com.nexus.pdv.audit.domain.AuditAction;
import com.nexus.pdv.customer.domain.Customer;
import com.nexus.pdv.customer.infrastructure.CustomerRepository;
import com.nexus.pdv.financial.api.FinancialDtos.EntryRequest;
import com.nexus.pdv.financial.api.FinancialDtos.EntryResponse;
import com.nexus.pdv.financial.api.FinancialDtos.SummaryResponse;
import com.nexus.pdv.financial.domain.FinancialEntry;
import com.nexus.pdv.financial.infrastructure.FinancialEntryRepository;
import com.nexus.pdv.plan.domain.FeatureCode;
import com.nexus.pdv.shared.access.AccessContext;
import com.nexus.pdv.shared.access.AccessContextService;
import com.nexus.pdv.shared.error.BusinessException;
import com.nexus.pdv.shared.error.ErrorCode;
import com.nexus.pdv.shared.text.Texts;
import com.nexus.pdv.shared.time.Period;
import com.nexus.pdv.supplier.domain.Supplier;
import com.nexus.pdv.supplier.infrastructure.SupplierRepository;
import com.nexus.pdv.tenant.application.TenantSettingsService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Contas a pagar e a receber do tenant. */
@Service
public class FinancialService {

    private final FinancialEntryRepository repository;
    private final CustomerRepository customerRepository;
    private final SupplierRepository supplierRepository;
    private final AccessContextService accessContextService;
    private final TenantSettingsService settingsService;
    private final AuditService auditService;
    private final Clock clock;

    public FinancialService(FinancialEntryRepository repository, CustomerRepository customerRepository,
            SupplierRepository supplierRepository, AccessContextService accessContextService,
            TenantSettingsService settingsService, AuditService auditService, Clock clock) {
        this.repository = repository;
        this.customerRepository = customerRepository;
        this.supplierRepository = supplierRepository;
        this.accessContextService = accessContextService;
        this.settingsService = settingsService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<EntryResponse> search(FinancialEntry.Type type, FinancialEntry.DisplayStatus status, String q,
            Period period, Pageable pageable) {
        LocalDate today = today();
        return repository.search(type, Texts.searchTerm(q), status == null ? null : status.name(), period.start(),
                period.end(), today, pageable).map(entry -> toResponse(entry, today));
    }

    @Transactional(readOnly = true)
    public EntryResponse get(UUID id) {
        return toResponse(find(id), today());
    }

    @Transactional
    public EntryResponse create(EntryRequest request) {
        AccessContext context = accessContextService.current();
        FinancialEntry entry = repository.save(new FinancialEntry(request.type(), Texts.clean(request.description()),
                Texts.clean(request.category()), request.amount(), request.dueDate(), Texts.clean(request.notes()),
                resolveCustomer(request.customerId()), resolveSupplier(context, request.supplierId()), context.userId()));
        auditService.record(AuditAction.CREATE, "FinancialEntry", entry.getId(),
                Map.of("type", entry.getType().name(), "amount", entry.getAmount()));
        return toResponse(entry, today());
    }

    @Transactional
    public EntryResponse update(UUID id, EntryRequest request) {
        AccessContext context = accessContextService.current();
        FinancialEntry entry = find(id);
        if (entry.getType() != request.type()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "O tipo da conta não pode ser alterado.");
        }
        entry.update(Texts.clean(request.description()), Texts.clean(request.category()), request.amount(),
                request.dueDate(), Texts.clean(request.notes()), resolveCustomer(request.customerId()),
                resolveSupplier(context, request.supplierId()));
        auditService.record(AuditAction.UPDATE, "FinancialEntry", id);
        return toResponse(entry, today());
    }

    @Transactional
    public EntryResponse pay(UUID id, LocalDate paymentDate) {
        FinancialEntry entry = find(id);
        LocalDate date = paymentDate != null ? paymentDate : today();
        if (date.isAfter(today())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "A data de pagamento não pode ser futura.");
        }
        entry.pay(date);
        auditService.record(AuditAction.UPDATE, "FinancialEntry", id, Map.of("operation", "pay", "date", date.toString()));
        return toResponse(entry, today());
    }

    @Transactional
    public EntryResponse cancel(UUID id) {
        FinancialEntry entry = find(id);
        entry.cancel();
        auditService.record(AuditAction.DISABLE, "FinancialEntry", id, Map.of("operation", "cancel"));
        return toResponse(entry, today());
    }

    @Transactional(readOnly = true)
    public SummaryResponse summary(Period period) {
        LocalDate today = today();
        BigDecimal revenue = repository.sumPaid(FinancialEntry.Type.RECEIVABLE, period.start(), period.end());
        BigDecimal expenses = repository.sumPaid(FinancialEntry.Type.PAYABLE, period.start(), period.end());
        Object[] receivable = repository.pendingTotals(FinancialEntry.Type.RECEIVABLE).get(0);
        Object[] receivableOverdue = repository.overdueTotals(FinancialEntry.Type.RECEIVABLE, today).get(0);
        Object[] payable = repository.pendingTotals(FinancialEntry.Type.PAYABLE).get(0);
        Object[] payableOverdue = repository.overdueTotals(FinancialEntry.Type.PAYABLE, today).get(0);
        return new SummaryResponse(period.start(), period.end(), revenue, expenses, revenue.subtract(expenses),
                (BigDecimal) receivable[0], ((Number) receivable[1]).longValue(),
                (BigDecimal) receivableOverdue[0], ((Number) receivableOverdue[1]).longValue(),
                (BigDecimal) payable[0], ((Number) payable[1]).longValue(),
                (BigDecimal) payableOverdue[0], ((Number) payableOverdue[1]).longValue());
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(settingsService.zone()));
    }

    private Customer resolveCustomer(UUID customerId) {
        if (customerId == null) {
            return null;
        }
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "Cliente inválido."));
    }

    private Supplier resolveSupplier(AccessContext context, UUID supplierId) {
        if (supplierId == null) {
            return null;
        }
        context.requireFeature(FeatureCode.SUPPLIERS);
        return supplierRepository.findById(supplierId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "Fornecedor inválido."));
    }

    private FinancialEntry find(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Conta não encontrada."));
    }

    static EntryResponse toResponse(FinancialEntry entry, LocalDate today) {
        return new EntryResponse(entry.getId(), entry.getType(), entry.getDescription(), entry.getCategory(),
                entry.getAmount(), entry.getDueDate(), entry.getPaymentDate(), entry.displayStatus(today),
                entry.getNotes(), entry.getCustomer() == null ? null : entry.getCustomer().getId(),
                entry.getCustomer() == null ? null : entry.getCustomer().getName(),
                entry.getSupplier() == null ? null : entry.getSupplier().getId(),
                entry.getSupplier() == null ? null : entry.getSupplier().displayName(), entry.getCreatedAt());
    }
}
