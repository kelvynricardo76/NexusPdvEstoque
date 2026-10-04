package com.nexus.pdv.financial.domain;

import com.nexus.pdv.customer.domain.Customer;
import com.nexus.pdv.shared.error.BusinessException;
import com.nexus.pdv.shared.error.ErrorCode;
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
import java.time.LocalDate;
import java.util.UUID;

/**
 * Conta a pagar ou a receber do tenant (financeiro OPERACIONAL do cliente).
 * Não tem relação com a cobrança da assinatura Nexus (billing).
 */
@Entity
@Table(name = "financial_entries")
public class FinancialEntry extends TenantVersionedEntity {

    public enum Type { PAYABLE, RECEIVABLE }

    /** Status armazenado. OVERDUE é derivado (PENDING com vencimento passado). */
    public enum Status { PENDING, PAID, CANCELED }

    /** Status exibido. */
    public enum DisplayStatus { PENDING, PAID, OVERDUE, CANCELED }

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 12, updatable = false)
    private Type type;

    @Column(name = "description", nullable = false, length = 200)
    private String description;

    @Column(name = "category", length = 80)
    private String category;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "payment_date")
    private LocalDate paymentDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status;

    @Column(name = "notes", length = 1000)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    protected FinancialEntry() {
    }

    public FinancialEntry(Type type, String description, String category, BigDecimal amount, LocalDate dueDate,
            String notes, Customer customer, Supplier supplier, UUID createdBy) {
        this.type = type;
        this.status = Status.PENDING;
        this.createdBy = createdBy;
        update(description, category, amount, dueDate, notes, customer, supplier);
    }

    public void update(String description, String category, BigDecimal amount, LocalDate dueDate, String notes,
            Customer customer, Supplier supplier) {
        if (status != Status.PENDING) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE, "Somente contas pendentes podem ser alteradas.");
        }
        this.description = description;
        this.category = category;
        this.amount = amount;
        this.dueDate = dueDate;
        this.notes = notes;
        this.customer = customer;
        this.supplier = supplier;
    }

    public void pay(LocalDate paymentDate) {
        if (status == Status.PAID) {
            return;
        }
        if (status == Status.CANCELED) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE, "Conta cancelada não pode ser baixada.");
        }
        this.status = Status.PAID;
        this.paymentDate = paymentDate;
    }

    public void cancel() {
        if (status == Status.PAID) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE, "Conta paga não pode ser cancelada.");
        }
        this.status = Status.CANCELED;
    }

    public DisplayStatus displayStatus(LocalDate today) {
        return switch (status) {
            case PAID -> DisplayStatus.PAID;
            case CANCELED -> DisplayStatus.CANCELED;
            case PENDING -> dueDate.isBefore(today) ? DisplayStatus.OVERDUE : DisplayStatus.PENDING;
        };
    }

    public Type getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public String getCategory() {
        return category;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public LocalDate getPaymentDate() {
        return paymentDate;
    }

    public Status getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }

    public Customer getCustomer() {
        return customer;
    }

    public Supplier getSupplier() {
        return supplier;
    }
}
