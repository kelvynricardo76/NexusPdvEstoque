package com.nexus.pdv.product.application;

import com.nexus.pdv.audit.application.AuditService;
import com.nexus.pdv.audit.domain.AuditAction;
import com.nexus.pdv.category.domain.Category;
import com.nexus.pdv.category.infrastructure.CategoryRepository;
import com.nexus.pdv.entitlement.EntitlementService;
import com.nexus.pdv.permission.domain.Permission;
import com.nexus.pdv.plan.domain.FeatureCode;
import com.nexus.pdv.plan.domain.LimitCode;
import com.nexus.pdv.product.api.CatalogDtos.ProductRequest;
import com.nexus.pdv.product.api.CatalogDtos.ProductResponse;
import com.nexus.pdv.product.domain.Product;
import com.nexus.pdv.product.domain.StockSituation;
import com.nexus.pdv.product.infrastructure.ProductRepository;
import com.nexus.pdv.shared.access.AccessContext;
import com.nexus.pdv.shared.access.AccessContextService;
import com.nexus.pdv.shared.error.BusinessException;
import com.nexus.pdv.shared.error.ErrorCode;
import com.nexus.pdv.shared.text.Texts;
import com.nexus.pdv.stock.application.StockService;
import com.nexus.pdv.stock.domain.StockMovementType;
import com.nexus.pdv.supplier.domain.Supplier;
import com.nexus.pdv.supplier.infrastructure.SupplierRepository;
import com.nexus.pdv.tenant.infrastructure.TenantRepository;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final SupplierRepository supplierRepository;
    private final TenantRepository tenantRepository;
    private final EntitlementService entitlementService;
    private final AccessContextService accessContextService;
    private final StockService stockService;
    private final AuditService auditService;

    public ProductService(ProductRepository productRepository, CategoryRepository categoryRepository,
            SupplierRepository supplierRepository, TenantRepository tenantRepository,
            EntitlementService entitlementService, AccessContextService accessContextService, StockService stockService,
            AuditService auditService) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.supplierRepository = supplierRepository;
        this.tenantRepository = tenantRepository;
        this.entitlementService = entitlementService;
        this.accessContextService = accessContextService;
        this.stockService = stockService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> search(String q, UUID categoryId, Boolean active, StockSituation situation,
            Pageable pageable) {
        boolean costView = accessContextService.current().hasPermission(Permission.PRODUCT_COST_VIEW);
        return productRepository.search(Texts.searchTerm(q), categoryId, active,
                        situation == null ? null : situation.name(), pageable)
                .map(product -> toResponse(product, costView));
    }

    @Transactional(readOnly = true)
    public ProductResponse get(UUID id) {
        return toResponse(find(id), accessContextService.current().hasPermission(Permission.PRODUCT_COST_VIEW));
    }

    /** Busca exata para o PDV / leitor: código de barras ou SKU de produto ativo. */
    @Transactional(readOnly = true)
    public Optional<ProductResponse> findByCode(String code) {
        String cleaned = Texts.clean(code);
        if (cleaned == null) {
            return Optional.empty();
        }
        boolean costView = accessContextService.current().hasPermission(Permission.PRODUCT_COST_VIEW);
        return productRepository.findFirstByBarcodeAndActiveTrue(cleaned)
                .or(() -> productRepository.findFirstBySkuIgnoreCaseAndActiveTrue(cleaned))
                .map(product -> toResponse(product, costView));
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        AccessContext context = accessContextService.current();
        boolean costView = context.hasPermission(Permission.PRODUCT_COST_VIEW);
        if (request.costPrice() != null && !costView) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Você não tem permissão para informar o custo.");
        }
        String sku = Texts.clean(request.sku());
        String barcode = Texts.clean(request.barcode());
        assertUniqueCodes(sku, barcode, null);

        tenantRepository.lockById(context.tenantId());
        entitlementService.assertWithinLimit(context.tenantId(), LimitCode.MAX_PRODUCTS, productRepository.count(), 1);

        Product product = productRepository.save(new Product(Texts.clean(request.name()), Texts.clean(request.description()),
                sku, barcode, resolveCategory(request.categoryId()), resolveSupplier(context, request.supplierId()),
                request.salePrice(), request.costPrice(), request.minimumStock(), request.unit()));
        BigDecimal initial = request.initialStock();
        if (initial != null && initial.signum() > 0) {
            assertQuantityMatchesUnit(product, initial);
            stockService.apply(product, StockMovementType.INITIAL, initial, "PRODUCT", product.getId(),
                    "Estoque inicial", true);
        }
        auditService.record(AuditAction.CREATE, "Product", product.getId(), Map.of("name", product.getName()));
        return toResponse(product, costView);
    }

    @Transactional
    public ProductResponse update(UUID id, ProductRequest request) {
        AccessContext context = accessContextService.current();
        boolean costView = context.hasPermission(Permission.PRODUCT_COST_VIEW);
        if (request.costPrice() != null && !costView) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Você não tem permissão para alterar o custo.");
        }
        Product product = find(id);
        String sku = Texts.clean(request.sku());
        String barcode = Texts.clean(request.barcode());
        assertUniqueCodes(sku, barcode, id);
        product.update(Texts.clean(request.name()), Texts.clean(request.description()), sku, barcode,
                resolveCategory(request.categoryId()), resolveSupplier(context, request.supplierId()),
                request.salePrice(), request.minimumStock(), request.unit());
        if (costView && request.costPrice() != null) {
            product.changeCostPrice(request.costPrice());
        }
        auditService.record(AuditAction.UPDATE, "Product", product.getId(), Map.of());
        return toResponse(product, costView);
    }

    @Transactional
    public ProductResponse setActive(UUID id, boolean active) {
        Product product = find(id);
        product.setActive(active);
        auditService.record(active ? AuditAction.ENABLE : AuditAction.DISABLE, "Product", id);
        return toResponse(product, accessContextService.current().hasPermission(Permission.PRODUCT_COST_VIEW));
    }

    public static void assertQuantityMatchesUnit(Product product, BigDecimal quantity) {
        if (product.getUnit().isDiscrete() && quantity.stripTrailingZeros().scale() > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "\"" + product.getName() + "\" é vendido por unidade; use quantidades inteiras.");
        }
    }

    private void assertUniqueCodes(String sku, String barcode, UUID ignoreId) {
        if (sku != null && (ignoreId == null ? productRepository.existsBySkuIgnoreCase(sku)
                : productRepository.existsBySkuIgnoreCaseAndIdNot(sku, ignoreId))) {
            throw new BusinessException(ErrorCode.CONFLICT, "Já existe um produto com este SKU.");
        }
        if (barcode != null && (ignoreId == null ? productRepository.existsByBarcode(barcode)
                : productRepository.existsByBarcodeAndIdNot(barcode, ignoreId))) {
            throw new BusinessException(ErrorCode.CONFLICT, "Já existe um produto com este código de barras.");
        }
    }

    private Category resolveCategory(UUID categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "Categoria inválida."));
    }

    private Supplier resolveSupplier(AccessContext context, UUID supplierId) {
        if (supplierId == null) {
            return null;
        }
        context.requireFeature(FeatureCode.SUPPLIERS);
        return supplierRepository.findById(supplierId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "Fornecedor inválido."));
    }

    private Product find(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Produto não encontrado."));
    }

    public static ProductResponse toResponse(Product product, boolean costView) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getSku(),
                product.getBarcode(),
                product.getCategory() == null ? null : product.getCategory().getId(),
                product.getCategory() == null ? null : product.getCategory().getName(),
                product.getSupplier() == null ? null : product.getSupplier().getId(),
                product.getSupplier() == null ? null : product.getSupplier().displayName(),
                product.getSalePrice(),
                costView ? product.getCostPrice() : null,
                product.getCurrentStock(),
                product.getMinimumStock(),
                product.getUnit(),
                product.isActive(),
                product.situation().name(),
                product.getCreatedAt(),
                product.getUpdatedAt());
    }
}
