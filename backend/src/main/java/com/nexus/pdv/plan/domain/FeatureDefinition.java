package com.nexus.pdv.plan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Entrada do catálogo de funcionalidades (nome e descrição editáveis). */
@Entity
@Table(name = "features")
public class FeatureDefinition {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "code", nullable = false, length = 50)
    private FeatureCode code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 300)
    private String description;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected FeatureDefinition() {
    }

    public void update(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public FeatureCode getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }
}
