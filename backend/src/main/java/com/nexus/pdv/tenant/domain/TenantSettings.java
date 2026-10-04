package com.nexus.pdv.tenant.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * Personalização e preferências operacionais do tenant. A chave é o próprio tenant_id, obtido
 * sempre do contexto autenticado.
 */
@Entity
@Table(name = "tenant_settings")
public class TenantSettings {

    public static final String DEFAULT_TIMEZONE = "America/Sao_Paulo";
    public static final String DEFAULT_CURRENCY = "BRL";

    @Id
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "company_name", nullable = false, length = 150)
    private String companyName;

    @Column(name = "trade_name", length = 150)
    private String tradeName;

    @Column(name = "logo_data_url", length = 400000)
    private String logoDataUrl;

    @Column(name = "primary_color", length = 7)
    private String primaryColor;

    @Column(name = "secondary_color", length = 7)
    private String secondaryColor;

    @Column(name = "document", length = 20)
    private String document;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "email", length = 254)
    private String email;

    @Column(name = "address", length = 300)
    private String address;

    @Column(name = "timezone", nullable = false, length = 60)
    private String timezone;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "allow_negative_stock", nullable = false)
    private boolean allowNegativeStock;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected TenantSettings() {
    }

    public TenantSettings(UUID tenantId, String companyName, String tradeName, String document, String email, String phone) {
        this.tenantId = tenantId;
        this.companyName = companyName;
        this.tradeName = tradeName;
        this.document = document;
        this.email = email;
        this.phone = phone;
        this.timezone = DEFAULT_TIMEZONE;
        this.currency = DEFAULT_CURRENCY;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public void update(String companyName, String tradeName, String document, String phone, String email,
            String address, String timezone, String currency, String primaryColor, String secondaryColor,
            boolean allowNegativeStock) {
        this.companyName = companyName;
        this.tradeName = tradeName;
        this.document = document;
        this.phone = phone;
        this.email = email;
        this.address = address;
        this.timezone = timezone;
        this.currency = currency;
        this.primaryColor = primaryColor;
        this.secondaryColor = secondaryColor;
        this.allowNegativeStock = allowNegativeStock;
    }

    public void changeLogo(String logoDataUrl) {
        this.logoDataUrl = logoDataUrl;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getCompanyName() {
        return companyName;
    }

    public String getTradeName() {
        return tradeName;
    }

    public String getLogoDataUrl() {
        return logoDataUrl;
    }

    public String getPrimaryColor() {
        return primaryColor;
    }

    public String getSecondaryColor() {
        return secondaryColor;
    }

    public String getDocument() {
        return document;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public String getAddress() {
        return address;
    }

    public String getTimezone() {
        return timezone;
    }

    public String getCurrency() {
        return currency;
    }

    public boolean isAllowNegativeStock() {
        return allowNegativeStock;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
