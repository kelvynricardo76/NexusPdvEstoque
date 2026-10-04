package com.nexus.pdv.sale.domain;

import com.nexus.pdv.shared.persistence.TenantOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** Item devolvido: quantidade, valor estornado e se voltou ao estoque (item avariado não volta). */
@Entity
@Table(name = "sale_return_items")
public class SaleReturnItem extends TenantOwnedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "return_id", nullable = false, updatable = false)
    private SaleReturn saleReturn;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_item_id", nullable = false, updatable = false)
    private SaleItem saleItem;

    @Column(name = "quantity", nullable = false, precision = 19, scale = 3, updatable = false)
    private BigDecimal quantity;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "restocked", nullable = false, updatable = false)
    private boolean restocked;

    protected SaleReturnItem() {
    }

    public SaleReturnItem(SaleItem saleItem, BigDecimal quantity, BigDecimal amount, boolean restocked) {
        this.saleItem = saleItem;
        this.quantity = quantity;
        this.amount = amount;
        this.restocked = restocked;
    }

    void attachTo(SaleReturn saleReturn) {
        this.saleReturn = saleReturn;
    }

    public SaleItem getSaleItem() {
        return saleItem;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public boolean isRestocked() {
        return restocked;
    }
}
