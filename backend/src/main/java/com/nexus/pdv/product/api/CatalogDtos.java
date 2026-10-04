package com.nexus.pdv.product.api;

import com.nexus.pdv.product.domain.ProductUnit;
import com.nexus.pdv.stock.domain.StockMovementType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** DTOs de produtos, categorias, fornecedores e estoque. */
public final class CatalogDtos {

    private CatalogDtos() {
    }

    // ---------- Categorias ----------

    public record CategoryRequest(@NotBlank @Size(max = 80) String name, @Size(max = 300) String description) {
    }

    public record CategoryResponse(UUID id, String name, String description, boolean active, long productCount) {
    }

    public record ActiveRequest(boolean active) {
    }

    // ---------- Produtos ----------

    /**
     * {@code costPrice} só pode ser informado por quem possui PRODUCT_COST_VIEW.
     * {@code initialStock} só é aceito na criação (depois, apenas via módulo de estoque).
     */
    public record ProductRequest(
            @NotBlank @Size(max = 150) String name,
            @Size(max = 1000) String description,
            @Size(max = 60) String sku,
            @Size(max = 60) String barcode,
            UUID categoryId,
            UUID supplierId,
            @NotNull @DecimalMin("0.00") @Digits(integer = 15, fraction = 2) BigDecimal salePrice,
            @DecimalMin("0.00") @Digits(integer = 15, fraction = 2) BigDecimal costPrice,
            @DecimalMin("0.000") @Digits(integer = 15, fraction = 3) BigDecimal minimumStock,
            @DecimalMin("0.000") @Digits(integer = 15, fraction = 3) BigDecimal initialStock,
            @NotNull ProductUnit unit) {
    }

    /** {@code costPrice} é nulo quando o usuário não possui PRODUCT_COST_VIEW. */
    public record ProductResponse(
            UUID id,
            String name,
            String description,
            String sku,
            String barcode,
            UUID categoryId,
            String categoryName,
            UUID supplierId,
            String supplierName,
            BigDecimal salePrice,
            BigDecimal costPrice,
            BigDecimal currentStock,
            BigDecimal minimumStock,
            ProductUnit unit,
            boolean active,
            String situation,
            Instant createdAt,
            Instant updatedAt) {
    }

    // ---------- Fornecedores ----------

    public record SupplierRequest(
            @NotBlank @Size(max = 150) String legalName,
            @Size(max = 150) String tradeName,
            @Size(max = 20) String document,
            @Size(max = 30) String phone,
            @Email @Size(max = 254) String email,
            @Size(max = 300) String address,
            @Size(max = 1000) String notes) {
    }

    public record SupplierResponse(UUID id, String legalName, String tradeName, String document, String phone,
            String email, String address, String notes, boolean active, Instant createdAt) {
    }

    // ---------- Estoque ----------

    public record StockEntryRequest(
            @NotNull UUID productId,
            @NotNull @DecimalMin(value = "0.001") @Digits(integer = 15, fraction = 3) BigDecimal quantity,
            @Size(max = 300) String reason) {
    }

    /** Ajuste para uma quantidade exata (contagem de inventário). Motivo obrigatório. */
    public record StockAdjustRequest(
            @NotNull UUID productId,
            @NotNull @DecimalMin(value = "0.000") @Digits(integer = 15, fraction = 3) BigDecimal newQuantity,
            @NotBlank @Size(min = 3, max = 300) String reason) {
    }

    public record StockItemResponse(UUID productId, String name, String sku, String categoryName,
            BigDecimal currentStock, BigDecimal minimumStock, String unit, String situation, boolean active) {
    }

    public record StockMovementResponse(UUID id, UUID productId, String productName, StockMovementType type,
            BigDecimal quantity, BigDecimal previousStock, BigDecimal newStock, String referenceType,
            UUID referenceId, String reason, String userName, Instant createdAt) {
    }

    public record StockSummaryResponse(long inStock, long lowStock, long outOfStock) {
    }
}
