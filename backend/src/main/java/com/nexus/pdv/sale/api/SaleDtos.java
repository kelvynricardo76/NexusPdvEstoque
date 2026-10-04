package com.nexus.pdv.sale.api;

import com.nexus.pdv.sale.domain.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SaleDtos {

    private SaleDtos() {
    }

    /**
     * Pedido de finalização. O preço unitário NÃO é aceito do cliente: sempre vem do cadastro
     * do produto no momento da venda.
     */
    public record FinalizeSaleRequest(
            @NotEmpty @Size(max = 500) List<@Valid ItemRequest> items,
            UUID customerId,
            @DecimalMin("0.00") @Digits(integer = 15, fraction = 2) BigDecimal discount,
            @NotEmpty @Size(max = 10) List<@Valid PaymentRequest> payments) {
    }

    public record ItemRequest(
            @NotNull UUID productId,
            @NotNull @DecimalMin(value = "0.001") @Digits(integer = 12, fraction = 3) BigDecimal quantity,
            @DecimalMin("0.00") @Digits(integer = 15, fraction = 2) BigDecimal discount) {
    }

    public record PaymentRequest(
            @NotNull PaymentMethod method,
            @NotNull @DecimalMin(value = "0.01") @Digits(integer = 15, fraction = 2) BigDecimal amount) {
    }

    public record CancelSaleRequest(@NotBlank @Size(min = 3, max = 300) String reason) {
    }

    /**
     * Devolução total ou parcial. Os itens são identificados pela linha da venda; o valor estornado
     * é calculado pelo servidor (proporcional ao que foi efetivamente pago).
     */
    public record ReturnSaleRequest(
            @NotEmpty @Size(max = 500) List<@Valid ReturnItemRequest> items,
            @NotBlank @Size(min = 3, max = 300) String reason,
            @NotNull PaymentMethod refundMethod) {
    }

    /** {@code restock = false} para item avariado: registra a devolução sem voltar ao estoque. */
    public record ReturnItemRequest(
            @NotNull @Min(1) Integer lineNumber,
            @NotNull @DecimalMin(value = "0.001") @Digits(integer = 12, fraction = 3) BigDecimal quantity,
            Boolean restock) {
    }

    public record SaleReturnResponse(
            UUID id,
            Instant createdAt,
            String reason,
            PaymentMethod refundMethod,
            BigDecimal total,
            String userName,
            List<SaleReturnItemResponse> items) {
    }

    public record SaleReturnItemResponse(int lineNumber, String description, BigDecimal quantity, BigDecimal amount,
            boolean restocked) {
    }

    public record SaleSummary(
            UUID id,
            long number,
            Instant createdAt,
            String customerName,
            String operatorName,
            BigDecimal total,
            String status,
            String refundStatus) {
    }

    public record SaleResponse(
            UUID id,
            long number,
            Instant createdAt,
            UUID customerId,
            String customerName,
            UUID operatorId,
            String operatorName,
            BigDecimal subtotal,
            BigDecimal discount,
            BigDecimal total,
            BigDecimal changeAmount,
            String status,
            String cancelReason,
            Instant canceledAt,
            String canceledByName,
            String refundStatus,
            BigDecimal refundedTotal,
            List<SaleItemResponse> items,
            List<PaymentResponse> payments,
            List<SaleReturnResponse> returns) {
    }

    public record SaleItemResponse(UUID productId, int lineNumber, String description, String sku,
            BigDecimal quantity, BigDecimal unitPrice, BigDecimal discount, BigDecimal total,
            BigDecimal returnedQuantity) {
    }

    public record PaymentResponse(PaymentMethod method, BigDecimal amount, String status) {
    }

    /** Comprovante (não fiscal) para impressão no PDV. */
    public record ReceiptResponse(
            String companyName,
            String tradeName,
            String document,
            String address,
            String phone,
            String logoDataUrl,
            String primaryColor,
            SaleResponse sale) {
    }
}
