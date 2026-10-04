package com.nexus.pdv.tenant.domain;

import com.nexus.pdv.plan.domain.FeatureCode;
import com.nexus.pdv.plan.domain.LimitCode;
import com.nexus.pdv.shared.persistence.VersionedEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.Table;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/** Empresa cliente da plataforma (tenant). */
@Entity
@Table(name = "tenants")
public class Tenant extends VersionedEntity {

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "trade_name", length = 150)
    private String tradeName;

    @Column(name = "document", length = 20)
    private String document;

    @Column(name = "email", length = 254)
    private String email;

    @Column(name = "phone", length = 30)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TenantStatus status;

    @Column(name = "status_reason", length = 300)
    private String statusReason;

    /** Exceções de features concedidas/revogadas pela Nexus (true = concede, false = revoga). */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tenant_feature_overrides", joinColumns = @JoinColumn(name = "tenant_id"))
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "feature_code", length = 50)
    @Column(name = "enabled", nullable = false)
    private Map<FeatureCode, Boolean> featureOverrides = new HashMap<>();

    /** Exceções de limites (-1 = ilimitado). */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tenant_limit_overrides", joinColumns = @JoinColumn(name = "tenant_id"))
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "limit_code", length = 50)
    @Column(name = "limit_value", nullable = false)
    private Map<LimitCode, Long> limitOverrides = new HashMap<>();

    protected Tenant() {
    }

    public Tenant(String name, String tradeName, String document, String email, String phone) {
        updateInfo(name, tradeName, document, email, phone);
        this.status = TenantStatus.ACTIVE;
    }

    public void updateInfo(String name, String tradeName, String document, String email, String phone) {
        this.name = name;
        this.tradeName = tradeName;
        this.document = document;
        this.email = email;
        this.phone = phone;
    }

    public void changeStatus(TenantStatus newStatus, String reason) {
        this.status = newStatus;
        this.statusReason = reason;
    }

    public void setFeatureOverride(FeatureCode feature, Boolean enabled) {
        if (enabled == null) {
            featureOverrides.remove(feature);
        } else {
            featureOverrides.put(feature, enabled);
        }
    }

    public void setLimitOverride(LimitCode limit, Long value) {
        if (value == null) {
            limitOverrides.remove(limit);
        } else {
            limitOverrides.put(limit, value);
        }
    }

    public String displayName() {
        return tradeName != null && !tradeName.isBlank() ? tradeName : name;
    }

    public String getName() {
        return name;
    }

    public String getTradeName() {
        return tradeName;
    }

    public String getDocument() {
        return document;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public TenantStatus getStatus() {
        return status;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public Map<FeatureCode, Boolean> getFeatureOverrides() {
        return featureOverrides.isEmpty() ? Map.of() : Collections.unmodifiableMap(new EnumMap<>(featureOverrides));
    }

    public Map<LimitCode, Long> getLimitOverrides() {
        return limitOverrides.isEmpty() ? Map.of() : Collections.unmodifiableMap(new EnumMap<>(limitOverrides));
    }
}
