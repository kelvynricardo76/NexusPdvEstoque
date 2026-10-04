package com.nexus.pdv.plan.domain;

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
import java.math.BigDecimal;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Pacote comercial: conjunto de features + limites. Nunca referenciado por código em regras
 * de negócio — as regras consultam o {@code EntitlementService}.
 */
@Entity
@Table(name = "plans")
public class Plan extends VersionedEntity {

    @Column(name = "code", nullable = false, length = 40, updatable = false)
    private String code;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "monthly_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal monthlyPrice;

    @Column(name = "annual_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal annualPrice;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "plan_features", joinColumns = @JoinColumn(name = "plan_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "feature_code", nullable = false, length = 50)
    private Set<FeatureCode> features = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "plan_limits", joinColumns = @JoinColumn(name = "plan_id"))
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "limit_code", length = 50)
    @Column(name = "limit_value", nullable = false)
    private Map<LimitCode, Long> limits = new HashMap<>();

    protected Plan() {
    }

    public Plan(String code, String name, String description, BigDecimal monthlyPrice, BigDecimal annualPrice,
            boolean active, int displayOrder) {
        this.code = code;
        update(name, description, monthlyPrice, annualPrice, active, displayOrder);
    }

    public void update(String name, String description, BigDecimal monthlyPrice, BigDecimal annualPrice,
            boolean active, int displayOrder) {
        this.name = name;
        this.description = description;
        this.monthlyPrice = monthlyPrice;
        this.annualPrice = annualPrice;
        this.active = active;
        this.displayOrder = displayOrder;
    }

    public void replaceFeatures(Set<FeatureCode> newFeatures) {
        features.clear();
        features.addAll(newFeatures);
    }

    public void replaceLimits(Map<LimitCode, Long> newLimits) {
        limits.clear();
        limits.putAll(newLimits);
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getMonthlyPrice() {
        return monthlyPrice;
    }

    public BigDecimal getAnnualPrice() {
        return annualPrice;
    }

    public boolean isActive() {
        return active;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public Set<FeatureCode> getFeatures() {
        return features.isEmpty() ? EnumSet.noneOf(FeatureCode.class) : Collections.unmodifiableSet(EnumSet.copyOf(features));
    }

    public Map<LimitCode, Long> getLimits() {
        return limits.isEmpty() ? Map.of() : Collections.unmodifiableMap(new EnumMap<>(limits));
    }
}
