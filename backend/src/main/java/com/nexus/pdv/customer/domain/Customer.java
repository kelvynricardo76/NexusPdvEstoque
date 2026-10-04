package com.nexus.pdv.customer.domain;

import com.nexus.pdv.shared.persistence.TenantVersionedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Cliente do tenant. Apenas dados necessários à operação são coletados (minimização/LGPD). */
@Entity
@Table(name = "customers")
public class Customer extends TenantVersionedEntity {

    @Column(name = "name", nullable = false, length = 150)
    private String name;

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

    protected Customer() {
    }

    public Customer(String name, String document, String phone, String email, String address, String notes) {
        update(name, document, phone, email, address, notes);
    }

    public void update(String name, String document, String phone, String email, String address, String notes) {
        this.name = name;
        this.document = document;
        this.phone = phone;
        this.email = email;
        this.address = address;
        this.notes = notes;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getName() {
        return name;
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
