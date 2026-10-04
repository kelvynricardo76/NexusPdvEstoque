package com.nexus.pdv.supplier.domain;

import com.nexus.pdv.shared.persistence.TenantVersionedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "suppliers")
public class Supplier extends TenantVersionedEntity {

    @Column(name = "legal_name", nullable = false, length = 150)
    private String legalName;

    @Column(name = "trade_name", length = 150)
    private String tradeName;

    @Column(name = "document", length = 20)
    private String document;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "email", length = 254)
    private String email;

    @Column(name = "address", length = 300)
    private String address;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected Supplier() {
    }

    public Supplier(String legalName, String tradeName, String document, String phone, String email, String address,
            String notes) {
        update(legalName, tradeName, document, phone, email, address, notes);
    }

    public void update(String legalName, String tradeName, String document, String phone, String email, String address,
            String notes) {
        this.legalName = legalName;
        this.tradeName = tradeName;
        this.document = document;
        this.phone = phone;
        this.email = email;
        this.address = address;
        this.notes = notes;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String displayName() {
        return tradeName != null && !tradeName.isBlank() ? tradeName : legalName;
    }

    public String getLegalName() {
        return legalName;
    }

    public String getTradeName() {
        return tradeName;
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

    public String getNotes() {
        return notes;
    }

    public boolean isActive() {
        return active;
    }
}
