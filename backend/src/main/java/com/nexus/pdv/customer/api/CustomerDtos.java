package com.nexus.pdv.customer.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CustomerDtos {

    private CustomerDtos() {
    }

    public record CustomerRequest(
            @NotBlank @Size(max = 150) String name,
            @Size(max = 20) String document,
            @Size(max = 30) String phone,
            @Email @Size(max = 254) String email,
            @Size(max = 300) String address,
            @Size(max = 1000) String notes) {
    }

    /** Em listagens o documento é mascarado (minimização de dados). */
    public record CustomerSummary(UUID id, String name, String documentMasked, String phone, String email,
            boolean active) {
    }

    public record CustomerDetail(
            UUID id,
            String name,
            String document,
            String phone,
            String email,
            String address,
            String notes,
            boolean active,
            Instant createdAt,
            long purchases,
            BigDecimal totalPurchased,
            Instant lastPurchaseAt,
            List<PurchaseEntry> recentPurchases) {
    }

    public record PurchaseEntry(UUID saleId, long number, Instant createdAt, BigDecimal total, String status) {
    }
}
