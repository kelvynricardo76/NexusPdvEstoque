package com.nexus.pdv.category.application;

import com.nexus.pdv.audit.application.AuditService;
import com.nexus.pdv.audit.domain.AuditAction;
import com.nexus.pdv.category.domain.Category;
import com.nexus.pdv.category.infrastructure.CategoryRepository;
import com.nexus.pdv.product.api.CatalogDtos.CategoryRequest;
import com.nexus.pdv.product.api.CatalogDtos.CategoryResponse;
import com.nexus.pdv.shared.error.BusinessException;
import com.nexus.pdv.shared.error.ErrorCode;
import com.nexus.pdv.shared.text.Texts;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

    private final CategoryRepository repository;
    private final AuditService auditService;

    public CategoryService(CategoryRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list(String q, Boolean active) {
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : repository.countProductsByCategory()) {
            counts.put((UUID) row[0], (Long) row[1]);
        }
        return repository.search(Texts.searchTerm(q), active).stream()
                .map(category -> toResponse(category, counts.getOrDefault(category.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse get(UUID id) {
        return toResponse(find(id), 0L);
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        String name = Texts.clean(request.name());
        if (repository.existsByNameIgnoreCase(name)) {
            throw new BusinessException(ErrorCode.CONFLICT, "Já existe uma categoria com este nome.");
        }
        Category category = repository.save(new Category(name, Texts.clean(request.description())));
        auditService.record(AuditAction.CREATE, "Category", category.getId(), Map.of("name", name));
        return toResponse(category, 0L);
    }

    @Transactional
    public CategoryResponse update(UUID id, CategoryRequest request) {
        Category category = find(id);
        String name = Texts.clean(request.name());
        if (repository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new BusinessException(ErrorCode.CONFLICT, "Já existe uma categoria com este nome.");
        }
        category.update(name, Texts.clean(request.description()));
        auditService.record(AuditAction.UPDATE, "Category", id);
        return toResponse(category, 0L);
    }

    @Transactional
    public CategoryResponse setActive(UUID id, boolean active) {
        Category category = find(id);
        category.setActive(active);
        auditService.record(active ? AuditAction.ENABLE : AuditAction.DISABLE, "Category", id);
        return toResponse(category, 0L);
    }

    private Category find(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Categoria não encontrada."));
    }

    private static CategoryResponse toResponse(Category category, long productCount) {
        return new CategoryResponse(category.getId(), category.getName(), category.getDescription(), category.isActive(),
                productCount);
    }
}
