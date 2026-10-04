-- FINANCEIRO operacional do cliente (contas a pagar e a receber).
-- Uma única tabela com discriminador "type": as duas contas têm exatamente o mesmo formato.
-- Não confundir com BILLING (subscription_invoices), que é a cobrança da assinatura Nexus.
-- OVERDUE não é armazenado: é derivado de PENDING + vencimento passado.

CREATE TABLE financial_entries (
    id              UUID          NOT NULL,
    tenant_id       UUID          NOT NULL,
    type            VARCHAR(12)   NOT NULL,
    description     VARCHAR(200)  NOT NULL,
    category        VARCHAR(80),
    amount          NUMERIC(19,2) NOT NULL,
    due_date        DATE          NOT NULL,
    payment_date    DATE,
    status          VARCHAR(20)   NOT NULL,
    notes           VARCHAR(1000),
    customer_id     UUID,
    supplier_id     UUID,
    created_by      UUID,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    version         BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_financial_entries PRIMARY KEY (id),
    CONSTRAINT fk_financial_entries_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_financial_entries_customer FOREIGN KEY (tenant_id, customer_id) REFERENCES customers (tenant_id, id),
    CONSTRAINT fk_financial_entries_supplier FOREIGN KEY (tenant_id, supplier_id) REFERENCES suppliers (tenant_id, id),
    CONSTRAINT ck_financial_entries_type CHECK (type IN ('PAYABLE', 'RECEIVABLE')),
    CONSTRAINT ck_financial_entries_amount CHECK (amount > 0)
);
CREATE INDEX ix_financial_entries_tenant_due ON financial_entries (tenant_id, type, status, due_date);
CREATE INDEX ix_financial_entries_tenant_paid ON financial_entries (tenant_id, type, payment_date);
