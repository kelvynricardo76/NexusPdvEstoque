package com.nexus.pdv.plan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Entrada do catálogo de limites, com o valor padrão usado quando o plano não define. */
@Entity
@Table(name = "limit_definitions")
public class LimitDefinition {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "code", nullable = false, length = 50)
    private LimitCode code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 300)
    private String description;

    @Column(name = "unit", length = 20)
    private String unit;

    @Column(name = "default_value", nullable = false)
    private long defaultValue;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected LimitDefinition() {
    }

    public void update(String name, String description, long defaultValue) {
        this.name = name;
        this.description = description;
        this.defaultValue = defaultValue;
    }

    public LimitCode getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getUnit() {
        return unit;
    }

    public long getDefaultValue() {
        return defaultValue;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }
}
