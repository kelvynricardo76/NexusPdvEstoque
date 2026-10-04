package com.nexus.pdv.financial.api;

import com.nexus.pdv.financial.domain.FinancialEntry;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class FinancialDtos {

    private FinancialDtos() {
    }

    public record EntryRequest(
            @NotNull FinancialEntry.Type type,
            @NotBlank @Size(max = 200) String description,
            @Size(max = 80) String category,
            @NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 2) BigDecimal amount,
            @NotNull LocalDate dueDate,
            @Size(max = 1000) String notes,
            UUID customerId,
            UUID supplierId) {
    }

    public record PayRequest(LocalDate paymentDate) {
    }

    public record EntryResponse(
            UUID id,
            FinancialEntry.Type type,
            String description,
            String category,
            BigDecimal amount,
            LocalDate dueDate,
            LocalDate paymentDate,
            FinancialEntry.DisplayStatus status,
            String notes,
            UUID customerId,
            String customerName,
            UUID supplierId,
            String supplierName,
            Instant createdAt) {
    }

    /** Receitas/despesas pagas no período; a receber/a pagar em aberto (total e vencidas). */
    public record SummaryResponse(
            LocalDate from,
            LocalDate to,
            BigDecimal revenue,
            BigDecimal expenses,
            BigDecimal balance,
            BigDecimal receivablePending,
            long receivablePendingCount,
            BigDecimal receivableOverdue,
            long receivableOverdueCount,
            BigDecimal payablePending,
            long payablePendingCount,
            BigDecimal payableOverdue,
            long payableOverdueCount) {
    }
}
