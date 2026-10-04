package com.nexus.pdv.sale.domain;

import com.nexus.pdv.shared.persistence.TenantOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** Pagamento de uma venda. Uma venda pode ter vários (pagamento dividido). */
@Entity
@Table(name = "payments")
public class Payment extends TenantOwnedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", nullable = false, updatable = false)
    private Sale sale;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 20, updatable = false)
    private PaymentMethod method;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    protected Payment() {
    }

    public Payment(PaymentMethod method, BigDecimal amount) {
        this.method = method;
        this.amount = amount;
        this.status = PaymentStatus.CONFIRMED;
    }

    void attachTo(Sale sale) {
        this.sale = sale;
    }

    void cancel() {
        this.status = PaymentStatus.CANCELED;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PaymentStatus getStatus() {
        return status;
    }
}
