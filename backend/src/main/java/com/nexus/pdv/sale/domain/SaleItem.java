package com.nexus.pdv.sale.domain;

import com.nexus.pdv.product.domain.Product;
import com.nexus.pdv.shared.persistence.TenantOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** Item de venda com snapshot de descrição, preço e custo no momento da venda. */
@Entity
@Table(name = "sale_items")
public class SaleItem extends TenantOwnedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", nullable = false, updatable = false)
    private Sale sale;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private Product product;

    @Column(name = "line_number", nullable = false, updatable = false)
    private int lineNumber;

    @Column(name = "description_snapshot", nullable = false, length = 150, updatable = false)
    private String descriptionSnapshot;

    @Column(name = "sku_snapshot", length = 60, updatable = false)
    private String skuSnapshot;

    @Column(name = "quantity", nullable = false, precision = 19, scale = 3, updatable = false)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal unitPrice;

    @Column(name = "cost_price_snapshot", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal costPriceSnapshot;

    @Column(name = "discount", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal discount;

    @Column(name = "total", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal total;

    @Column(name = "returned_quantity", nullable = false, precision = 19, scale = 3)
    private BigDecimal returnedQuantity = BigDecimal.ZERO;

    @Column(name = "refunded_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    protected SaleItem() {
    }

    public SaleItem(Product product, int lineNumber, BigDecimal quantity, BigDecimal unitPrice, BigDecimal discount,
            BigDecimal total) {
        this.product = product;
        this.lineNumber = lineNumber;
        this.descriptionSnapshot = product.getName();
        this.skuSnapshot = product.getSku();
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.costPriceSnapshot = product.getCostPrice();
        this.discount = discount;
        this.total = total;
    }

    void attachTo(Sale sale) {
        this.sale = sale;
    }

    public Sale getSale() {
        return sale;
    }

    public Product getProduct() {
        return product;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public String getDescriptionSnapshot() {
        return descriptionSnapshot;
    }

    public String getSkuSnapshot() {
        return skuSnapshot;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getCostPriceSnapshot() {
        return costPriceSnapshot;
    }

    public BigDecimal getDiscount() {
        return discount;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public BigDecimal getReturnedQuantity() {
        return returnedQuantity;
    }

    public BigDecimal getRefundedAmount() {
        return refundedAmount;
    }

    /** Quantidade ainda passível de devolução. */
    public BigDecimal returnableQuantity() {
        return quantity.subtract(returnedQuantity);
    }

    void registerReturn(BigDecimal returned, BigDecimal refunded) {
        if (returned.compareTo(returnableQuantity()) > 0) {
            throw new IllegalStateException("Quantidade devolvida maior que a vendida.");
        }
        this.returnedQuantity = returnedQuantity.add(returned);
        this.refundedAmount = refundedAmount.add(refunded);
    }
}
