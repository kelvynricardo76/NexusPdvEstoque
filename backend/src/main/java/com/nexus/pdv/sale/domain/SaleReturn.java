package com.nexus.pdv.sale.domain;

import com.nexus.pdv.shared.persistence.TenantOwnedEntity;
import com.nexus.pdv.user.domain.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Devolução (total ou parcial) de itens de uma venda concluída. Imutável após registrada. */
@Entity
@Table(name = "sale_returns")
public class SaleReturn extends TenantOwnedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", nullable = false, updatable = false)
    private Sale sale;

    @Column(name = "reason", nullable = false, length = 300, updatable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "refund_method", nullable = false, length = 20, updatable = false)
    private PaymentMethod refundMethod;

    @Column(name = "total", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal total;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "idempotency_key", length = 80, updatable = false)
    private String idempotencyKey;

    @OneToMany(mappedBy = "saleReturn", cascade = CascadeType.ALL, orphanRemoval = false)
    private List<SaleReturnItem> items = new ArrayList<>();

    protected SaleReturn() {
    }

    public SaleReturn(Sale sale, String reason, PaymentMethod refundMethod, User user, String idempotencyKey) {
        this.sale = sale;
        this.reason = reason;
        this.refundMethod = refundMethod;
        this.user = user;
        this.idempotencyKey = idempotencyKey;
        this.total = BigDecimal.ZERO.setScale(2);
    }

    public void addItem(SaleReturnItem item) {
        item.attachTo(this);
        items.add(item);
        total = total.add(item.getAmount());
    }

    public Sale getSale() {
        return sale;
    }

    public String getReason() {
        return reason;
    }

    public PaymentMethod getRefundMethod() {
        return refundMethod;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public User getUser() {
        return user;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public List<SaleReturnItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
