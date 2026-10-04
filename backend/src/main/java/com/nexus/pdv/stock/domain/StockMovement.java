package com.nexus.pdv.stock.domain;

import com.nexus.pdv.product.domain.Product;
import com.nexus.pdv.shared.persistence.TenantOwnedEntity;
import com.nexus.pdv.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/** Registro imutável de uma alteração de estoque (quantidade sempre positiva; direção pelo tipo). */
@Entity
@Table(name = "stock_movements")
public class StockMovement extends TenantOwnedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30, updatable = false)
    private StockMovementType type;

    @Column(name = "quantity", nullable = false, precision = 19, scale = 3, updatable = false)
    private BigDecimal quantity;

    @Column(name = "previous_stock", nullable = false, precision = 19, scale = 3, updatable = false)
    private BigDecimal previousStock;

    @Column(name = "new_stock", nullable = false, precision = 19, scale = 3, updatable = false)
    private BigDecimal newStock;

    @Column(name = "reference_type", length = 30, updatable = false)
    private String referenceType;

    @Column(name = "reference_id", updatable = false)
    private UUID referenceId;

    @Column(name = "reason", length = 300, updatable = false)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", updatable = false)
    private User user;

    protected StockMovement() {
    }

    public StockMovement(Product product, StockMovementType type, BigDecimal quantity, BigDecimal previousStock,
            BigDecimal newStock, String referenceType, UUID referenceId, String reason, User user) {
        this.product = product;
        this.type = type;
        this.quantity = quantity;
        this.previousStock = previousStock;
        this.newStock = newStock;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.reason = reason;
        this.user = user;
    }

    public Product getProduct() {
        return product;
    }

    public StockMovementType getType() {
        return type;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getPreviousStock() {
        return previousStock;
    }

    public BigDecimal getNewStock() {
        return newStock;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public UUID getReferenceId() {
        return referenceId;
    }

    public String getReason() {
        return reason;
    }

    public User getUser() {
        return user;
    }
}
