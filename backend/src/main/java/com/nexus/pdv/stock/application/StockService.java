package com.nexus.pdv.stock.application;

import com.nexus.pdv.audit.application.AuditService;
import com.nexus.pdv.audit.domain.AuditAction;
import com.nexus.pdv.product.api.CatalogDtos.StockAdjustRequest;
import com.nexus.pdv.product.api.CatalogDtos.StockEntryRequest;
import com.nexus.pdv.product.api.CatalogDtos.StockItemResponse;
import com.nexus.pdv.product.api.CatalogDtos.StockMovementResponse;
import com.nexus.pdv.product.api.CatalogDtos.StockSummaryResponse;
import com.nexus.pdv.product.domain.Product;
import com.nexus.pdv.product.domain.StockSituation;
import com.nexus.pdv.product.infrastructure.ProductRepository;
import com.nexus.pdv.shared.error.BusinessException;
import com.nexus.pdv.shared.error.ErrorCode;
import com.nexus.pdv.shared.security.CurrentUser;
import com.nexus.pdv.shared.text.Texts;
import com.nexus.pdv.shared.time.Period;
import com.nexus.pdv.stock.domain.StockMovement;
import com.nexus.pdv.stock.domain.StockMovementType;
import com.nexus.pdv.stock.infrastructure.StockMovementRepository;
import com.nexus.pdv.tenant.application.TenantSettingsService;
import com.nexus.pdv.user.domain.User;
import com.nexus.pdv.user.infrastructure.UserRepository;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Único ponto de alteração de estoque. Toda mudança:
 * <ol>
 *   <li>ocorre com o produto travado (PESSIMISTIC_WRITE);</li>
 *   <li>respeita a política de estoque negativo do tenant;</li>
 *   <li>gera um {@link StockMovement} com saldo anterior e novo.</li>
 * </ol>
 */
@Service
public class StockService {

    private final ProductRepository productRepository;
    private final StockMovementRepository movementRepository;
    private final UserRepository userRepository;
    private final TenantSettingsService settingsService;
    private final AuditService auditService;

    public StockService(ProductRepository productRepository, StockMovementRepository movementRepository,
            UserRepository userRepository, TenantSettingsService settingsService, AuditService auditService) {
        this.productRepository = productRepository;
        this.movementRepository = movementRepository;
        this.userRepository = userRepository;
        this.settingsService = settingsService;
        this.auditService = auditService;
    }

    /**
     * Aplica uma variação de estoque a um produto JÁ TRAVADO pela transação chamadora.
     *
     * @param delta positivo = entrada, negativo = saída
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public StockMovement apply(Product lockedProduct, StockMovementType type, BigDecimal delta, String referenceType,
            UUID referenceId, String reason, boolean allowNegative) {
        if (delta.signum() == 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "A quantidade deve ser diferente de zero.");
        }
        BigDecimal previous = lockedProduct.getCurrentStock();
        BigDecimal next = previous.add(delta);
        if (next.signum() < 0 && !allowNegative) {
            throw new BusinessException(ErrorCode.STOCK_INSUFFICIENT,
                    "Estoque insuficiente para \"" + lockedProduct.getName() + "\" (disponível: "
                            + previous.stripTrailingZeros().toPlainString() + ").");
        }
        lockedProduct.applyStock(next);
        User user = CurrentUser.find()
                .filter(principal -> !principal.isPlatformAdmin())
                .flatMap(principal -> userRepository.findById(principal.id()))
                .orElse(null);
        return movementRepository.save(new StockMovement(lockedProduct, type, delta.abs(), previous, next,
                referenceType, referenceId, reason, user));
    }

    public boolean allowNegativeStock() {
        return settingsService.allowNegativeStock();
    }

    @Transactional
    public StockMovementResponse registerEntry(StockEntryRequest request) {
        Product product = lock(request.productId());
        StockMovement movement = apply(product, StockMovementType.ENTRY, request.quantity(), "MANUAL", null,
                Texts.clean(request.reason()), true);
        auditService.record(AuditAction.STOCK_ENTRY, "Product", product.getId(),
                Map.of("quantity", request.quantity(), "newStock", movement.getNewStock()));
        return toResponse(movement);
    }

    @Transactional
    public StockMovementResponse adjust(StockAdjustRequest request) {
        Product product = lock(request.productId());
        BigDecimal delta = request.newQuantity().subtract(product.getCurrentStock());
        if (delta.signum() == 0) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE, "O estoque já está nesta quantidade.");
        }
        StockMovementType type = delta.signum() > 0 ? StockMovementType.POSITIVE_ADJUSTMENT : StockMovementType.NEGATIVE_ADJUSTMENT;
        StockMovement movement = apply(product, type, delta, "ADJUSTMENT", null, Texts.clean(request.reason()), false);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("previous", movement.getPreviousStock());
        meta.put("new", movement.getNewStock());
        meta.put("reason", Texts.clean(request.reason()));
        auditService.record(AuditAction.STOCK_ADJUST, "Product", product.getId(), meta);
        return toResponse(movement);
    }

    @Transactional(readOnly = true)
    public Page<StockItemResponse> list(String q, StockSituation situation, UUID categoryId, Pageable pageable) {
        return productRepository.search(Texts.searchTerm(q), categoryId, Boolean.TRUE,
                        situation == null ? null : situation.name(), pageable)
                .map(product -> new StockItemResponse(product.getId(), product.getName(), product.getSku(),
                        product.getCategory() == null ? null : product.getCategory().getName(),
                        product.getCurrentStock(), product.getMinimumStock(), product.getUnit().name(),
                        product.situation().name(), product.isActive()));
    }

    @Transactional(readOnly = true)
    public StockSummaryResponse summary() {
        return new StockSummaryResponse(productRepository.countInStock(), productRepository.countLowStock(),
                productRepository.countOutOfStock());
    }

    @Transactional(readOnly = true)
    public Page<StockMovementResponse> movements(UUID productId, StockMovementType type, Period period, Pageable pageable) {
        return movementRepository.search(productId, type, period.startInstant(), period.endExclusive(), pageable)
                .map(StockService::toResponse);
    }

    private Product lock(UUID productId) {
        return productRepository.lockById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Produto não encontrado."));
    }

    public static StockMovementResponse toResponse(StockMovement movement) {
        return new StockMovementResponse(movement.getId(), movement.getProduct().getId(), movement.getProduct().getName(),
                movement.getType(), movement.getQuantity(), movement.getPreviousStock(), movement.getNewStock(),
                movement.getReferenceType(), movement.getReferenceId(), movement.getReason(),
                movement.getUser() == null ? null : movement.getUser().getName(), movement.getCreatedAt());
    }
}
