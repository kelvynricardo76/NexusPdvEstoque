package com.nexus.pdv.product.domain;

import com.nexus.pdv.category.domain.Category;
import com.nexus.pdv.shared.persistence.TenantVersionedEntity;
import com.nexus.pdv.supplier.domain.Supplier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * Produto do tenant. O estoque atual ({@code currentStock}) só é alterado pelo módulo de estoque,
 * que sempre registra o movimento correspondente.
 */
@Entity
@Table(name = "products")
public class Product extends TenantVersionedEntity {

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "sku", length = 60)
    private String sku;

    @Column(name = "barcode", length = 60)
    private String barcode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @Column(name = "sale_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal salePrice;

    @Column(name = "cost_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal costPrice = BigDecimal.ZERO;

    @Column(name = "current_stock", nullable = false, precision = 19, scale = 3)
    private BigDecimal currentStock = BigDecimal.ZERO;

    @Column(name = "minimum_stock", nullable = false, precision = 19, scale = 3)
    private BigDecimal minimumStock = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit", nullable = false, length = 10)
    private ProductUnit unit;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected Product() {
    }

    public Product(String name, String description, String sku, String barcode, Category category, Supplier supplier,
            BigDecimal salePrice, BigDecimal costPrice, BigDecimal minimumStock, ProductUnit unit) {
        update(name, description, sku, barcode, category, supplier, salePrice, minimumStock, unit);
        this.costPrice = costPrice == null ? BigDecimal.ZERO : costPrice;
    }

    public void update(String name, String description, String sku, String barcode, Category category,
            Supplier supplier, BigDecimal salePrice, BigDecimal minimumStock, ProductUnit unit) {
        this.name = name;
        this.description = description;
        this.sku = sku;
        this.barcode = barcode;
        this.category = category;
        this.supplier = supplier;
        this.salePrice = salePrice;
        this.minimumStock = minimumStock == null ? BigDecimal.ZERO : minimumStock;
        this.unit = unit;
    }

    public void changeCostPrice(BigDecimal costPrice) {
        this.costPrice = costPrice;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    /** Uso exclusivo do StockService (que registra o movimento). */
    public void applyStock(BigDecimal newStock) {
        this.currentStock = newStock;
    }

    public StockSituation situation() {
        return StockSituation.of(currentStock, minimumStock);
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getSku() {
        return sku;
    }

    public String getBarcode() {
        return barcode;
    }

    public Category getCategory() {
        return category;
    }

    public Supplier getSupplier() {
        return supplier;
    }

    public BigDecimal getSalePrice() {
        return salePrice;
    }

    public BigDecimal getCostPrice() {
        return costPrice;
    }

    public BigDecimal getCurrentStock() {
        return currentStock;
    }

    public BigDecimal getMinimumStock() {
        return minimumStock;
    }

    public ProductUnit getUnit() {
        return unit;
    }

    public boolean isActive() {
        return active;
    }
}
