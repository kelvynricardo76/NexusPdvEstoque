package com.nexus.pdv.supplier.application;

import com.nexus.pdv.audit.application.AuditService;
import com.nexus.pdv.audit.domain.AuditAction;
import com.nexus.pdv.product.api.CatalogDtos.SupplierRequest;
import com.nexus.pdv.product.api.CatalogDtos.SupplierResponse;
import com.nexus.pdv.shared.error.BusinessException;
import com.nexus.pdv.shared.error.ErrorCode;
import com.nexus.pdv.shared.text.Documents;
import com.nexus.pdv.shared.text.Texts;
import com.nexus.pdv.supplier.domain.Supplier;
import com.nexus.pdv.supplier.infrastructure.SupplierRepository;
import com.nexus.pdv.user.domain.User;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SupplierService {

    private final SupplierRepository repository;
    private final AuditService auditService;

    public SupplierService(SupplierRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Page<SupplierResponse> search(String q, Boolean active, Pageable pageable) {
        return repository.search(Texts.searchTerm(q), active, pageable).map(SupplierService::toResponse);
    }

    @Transactional(readOnly = true)
    public SupplierResponse get(UUID id) {
        return toResponse(find(id));
    }

    @Transactional
    public SupplierResponse create(SupplierRequest request) {
        Supplier supplier = repository.save(new Supplier(Texts.clean(request.legalName()), Texts.clean(request.tradeName()),
                Documents.normalize(request.document()), Texts.clean(request.phone()),
                User.normalizeEmail(Texts.clean(request.email())), Texts.clean(request.address()),
                Texts.clean(request.notes())));
        auditService.record(AuditAction.CREATE, "Supplier", supplier.getId(), Map.of("name", supplier.getLegalName()));
        return toResponse(supplier);
    }

    @Transactional
    public SupplierResponse update(UUID id, SupplierRequest request) {
        Supplier supplier = find(id);
        supplier.update(Texts.clean(request.legalName()), Texts.clean(request.tradeName()),
                Documents.normalize(request.document()), Texts.clean(request.phone()),
                User.normalizeEmail(Texts.clean(request.email())), Texts.clean(request.address()),
                Texts.clean(request.notes()));
        auditService.record(AuditAction.UPDATE, "Supplier", id);
        return toResponse(supplier);
    }

    @Transactional
    public SupplierResponse setActive(UUID id, boolean active) {
        Supplier supplier = find(id);
        supplier.setActive(active);
        auditService.record(active ? AuditAction.ENABLE : AuditAction.DISABLE, "Supplier", id);
        return toResponse(supplier);
    }

    private Supplier find(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Fornecedor não encontrado."));
    }

    static SupplierResponse toResponse(Supplier supplier) {
        return new SupplierResponse(supplier.getId(), supplier.getLegalName(), supplier.getTradeName(),
                supplier.getDocument(), supplier.getPhone(), supplier.getEmail(), supplier.getAddress(),
                supplier.getNotes(), supplier.isActive(), supplier.getCreatedAt());
    }
}
