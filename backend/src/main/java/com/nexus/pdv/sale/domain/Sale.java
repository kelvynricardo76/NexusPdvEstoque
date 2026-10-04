package com.nexus.pdv.sale.domain;

import com.nexus.pdv.customer.domain.Customer;
import com.nexus.pdv.shared.persistence.TenantVersionedEntity;
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
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Venda. Nunca é removida: o cancelamento altera o status e preserva o registro original. */
@Entity
@Table(name = "sales")
public class Sale extends TenantVersionedEntity {

    @Column(name = "number", nullable = false, updatable = false)
    private long number;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", updatable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "operator_id", nullable = false, updatable = false)
    private User operator;

    @Column(name = "subtotal", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal subtotal;

    @Column(name = "discount", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal discount;

    @Column(name = "total", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal total;

    @Column(name = "change_amount", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal changeAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SaleStatus status;

    @Column(name = "idempotency_key", length = 80, updatable = false)
    private String idempotencyKey;

    @Column(name = "cancel_reason", length = 300)
    private String cancelReason;

    @Column(name = "canceled_at")
    private Instant canceledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "canceled_by")
    private User canceledBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "refund_status", nullable = false, length = 20)
    private SaleRefundStatus refundStatus = SaleRefundStatus.NONE;

    @Column(name = "refunded_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal refundedTotal = BigDecimal.ZERO;

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = false)
    @OrderBy("lineNumber ASC")
    private List<SaleItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = false)
    private List<Payment> payments = new ArrayList<>();

    protected Sale() {
    }

    public Sale(long number, Customer customer, User operator, BigDecimal subtotal, BigDecimal discount,
            BigDecimal total, BigDecimal changeAmount, String idempotencyKey) {
        this.number = number;
        this.customer = customer;
        this.operator = operator;
        this.subtotal = subtotal;
        this.discount = discount;
        this.total = total;
        this.changeAmount = changeAmount;
        this.idempotencyKey = idempotencyKey;
        this.status = SaleStatus.COMPLETED;
    }

    public void addItem(SaleItem item) {
        item.attachTo(this);
        items.add(item);
    }

    public void addPayment(Payment payment) {
        payment.attachTo(this);
        payments.add(payment);
    }

    public boolean isCanceled() {
        return status == SaleStatus.CANCELED;
    }

    public void cancel(String reason, User by, Instant when) {
        this.status = SaleStatus.CANCELED;
        this.cancelReason = reason;
        this.canceledBy = by;
        this.canceledAt = when;
        payments.forEach(Payment::cancel);
    }

    public boolean hasReturns() {
        return refundStatus != SaleRefundStatus.NONE;
    }

    /** Aplica uma devolução já validada: atualiza itens, total estornado e situação de devolução. */
    public void applyReturn(SaleReturn saleReturn) {
        for (SaleReturnItem returned : saleReturn.getItems()) {
            returned.getSaleItem().registerReturn(returned.getQuantity(), returned.getAmount());
        }
        refundedTotal = refundedTotal.add(saleReturn.getTotal());
        boolean everythingReturned = items.stream().allMatch(item -> item.returnableQuantity().signum() == 0);
        refundStatus = everythingReturned ? SaleRefundStatus.FULL : SaleRefundStatus.PARTIAL;
    }

    public long getNumber() {
        return number;
    }

    public Customer getCustomer() {
        return customer;
    }

    public User getOperator() {
        return operator;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getDiscount() {
        return discount;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public BigDecimal getChangeAmount() {
        return changeAmount;
    }

    public SaleStatus getStatus() {
        return status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public Instant getCanceledAt() {
        return canceledAt;
    }

    public User getCanceledBy() {
        return canceledBy;
    }

    public SaleRefundStatus getRefundStatus() {
        return refundStatus;
    }

    public BigDecimal getRefundedTotal() {
        return refundedTotal;
    }

    public List<SaleItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public List<Payment> getPayments() {
        return Collections.unmodifiableList(payments);
    }
}
