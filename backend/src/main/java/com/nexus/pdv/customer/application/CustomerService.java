package com.nexus.pdv.customer.application;

import com.nexus.pdv.audit.application.AuditService;
import com.nexus.pdv.audit.domain.AuditAction;
import com.nexus.pdv.customer.api.CustomerDtos.CustomerDetail;
import com.nexus.pdv.customer.api.CustomerDtos.CustomerRequest;
import com.nexus.pdv.customer.api.CustomerDtos.CustomerSummary;
import com.nexus.pdv.customer.api.CustomerDtos.PurchaseEntry;
import com.nexus.pdv.customer.domain.Customer;
import com.nexus.pdv.customer.infrastructure.CustomerRepository;
import com.nexus.pdv.sale.infrastructure.SaleRepository;
import com.nexus.pdv.shared.error.BusinessException;
import com.nexus.pdv.shared.error.ErrorCode;
import com.nexus.pdv.shared.text.Documents;
import com.nexus.pdv.shared.text.Texts;
import com.nexus.pdv.user.domain.User;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

    private final CustomerRepository repository;
    private final SaleRepository saleRepository;
    private final AuditService auditService;

    public CustomerService(CustomerRepository repository, SaleRepository saleRepository, AuditService auditService) {
        this.repository = repository;
        this.saleRepository = saleRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Page<CustomerSummary> search(String q, Boolean active, Pageable pageable) {
        String term = Texts.searchTerm(q);
        return repository.search(term, active, pageable).map(CustomerService::toSummary);
    }

    @Transactional(readOnly = true)
    public CustomerDetail detail(UUID id) {
        Customer customer = find(id);
        Object[] totals = saleRepository.customerTotals(id).get(0);
        List<PurchaseEntry> recent = saleRepository.search(null, null, id, null, Instant.EPOCH,
                        Instant.now().plusSeconds(86_400), PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(sale -> new PurchaseEntry(sale.getId(), sale.getNumber(), sale.getCreatedAt(), sale.getTotal(),
                        sale.getStatus().name()))
                .getContent();
        return new CustomerDetail(customer.getId(), customer.getName(), customer.getDocument(), customer.getPhone(),
                customer.getEmail(), customer.getAddress(), customer.getNotes(), customer.isActive(),
                customer.getCreatedAt(), ((Number) totals[0]).longValue(), (BigDecimal) totals[1], (Instant) totals[2],
                recent);
    }

    @Transactional
    public CustomerDetail create(CustomerRequest request) {
        Customer customer = repository.save(new Customer(Texts.clean(request.name()), Documents.normalize(request.document()),
                Texts.clean(request.phone()), User.normalizeEmail(Texts.clean(request.email())),
                Texts.clean(request.address()), Texts.clean(request.notes())));
        auditService.record(AuditAction.CREATE, "Customer", customer.getId());
        return detail(customer.getId());
    }

    @Transactional
    public CustomerDetail update(UUID id, CustomerRequest request) {
        Customer customer = find(id);
        customer.update(Texts.clean(request.name()), Documents.normalize(request.document()), Texts.clean(request.phone()),
                User.normalizeEmail(Texts.clean(request.email())), Texts.clean(request.address()),
                Texts.clean(request.notes()));
        auditService.record(AuditAction.UPDATE, "Customer", id);
        return detail(id);
    }

    @Transactional
    public CustomerDetail setActive(UUID id, boolean active) {
        Customer customer = find(id);
        customer.setActive(active);
        auditService.record(active ? AuditAction.ENABLE : AuditAction.DISABLE, "Customer", id, Map.of());
        return detail(id);
    }

    private Customer find(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Cliente não encontrado."));
    }

    static CustomerSummary toSummary(Customer customer) {
        return new CustomerSummary(customer.getId(), customer.getName(), Documents.mask(customer.getDocument()),
                customer.getPhone(), customer.getEmail(), customer.isActive());
    }
}
