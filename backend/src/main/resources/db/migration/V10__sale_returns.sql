-- Devoluções (totais ou parciais) de vendas concluídas.
-- A venda original permanece COMPLETED; o estado da devolução fica em refund_status e os
-- valores devolvidos são abatidos nos indicadores de faturamento.

ALTER TABLE sales ADD COLUMN refund_status VARCHAR(20) NOT NULL DEFAULT 'NONE';
ALTER TABLE sales ADD COLUMN refunded_total NUMERIC(19,2) NOT NULL DEFAULT 0;
ALTER TABLE sales ADD CONSTRAINT ck_sales_refunded CHECK (refunded_total >= 0 AND refunded_total <= total);

ALTER TABLE sale_items ADD COLUMN returned_quantity NUMERIC(19,3) NOT NULL DEFAULT 0;
ALTER TABLE sale_items ADD COLUMN refunded_amount NUMERIC(19,2) NOT NULL DEFAULT 0;
ALTER TABLE sale_items ADD CONSTRAINT uk_sale_items_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE sale_items ADD CONSTRAINT ck_sale_items_returned
    CHECK (returned_quantity >= 0 AND returned_quantity <= quantity AND refunded_amount >= 0);

CREATE TABLE sale_returns (
    id               UUID          NOT NULL,
    tenant_id        UUID          NOT NULL,
    sale_id          UUID          NOT NULL,
    reason           VARCHAR(300)  NOT NULL,
    refund_method    VARCHAR(20)   NOT NULL,
    total            NUMERIC(19,2) NOT NULL,
    user_id          UUID          NOT NULL,
    idempotency_key  VARCHAR(80),
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_sale_returns PRIMARY KEY (id),
    CONSTRAINT uk_sale_returns_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_sale_returns_tenant_idempotency UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT fk_sale_returns_sale FOREIGN KEY (tenant_id, sale_id) REFERENCES sales (tenant_id, id),
    CONSTRAINT fk_sale_returns_user FOREIGN KEY (tenant_id, user_id) REFERENCES users (tenant_id, id),
    CONSTRAINT ck_sale_returns_total CHECK (total >= 0)
);
CREATE INDEX ix_sale_returns_sale ON sale_returns (tenant_id, sale_id);
CREATE INDEX ix_sale_returns_created ON sale_returns (tenant_id, created_at);

CREATE TABLE sale_return_items (
    id               UUID          NOT NULL,
    tenant_id        UUID          NOT NULL,
    return_id        UUID          NOT NULL,
    sale_item_id     UUID          NOT NULL,
    quantity         NUMERIC(19,3) NOT NULL,
    amount           NUMERIC(19,2) NOT NULL,
    restocked        BOOLEAN       NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_sale_return_items PRIMARY KEY (id),
    CONSTRAINT fk_sale_return_items_return FOREIGN KEY (tenant_id, return_id) REFERENCES sale_returns (tenant_id, id),
    CONSTRAINT fk_sale_return_items_item FOREIGN KEY (tenant_id, sale_item_id) REFERENCES sale_items (tenant_id, id),
    CONSTRAINT ck_sale_return_items_values CHECK (quantity > 0 AND amount >= 0)
);
CREATE INDEX ix_sale_return_items_return ON sale_return_items (tenant_id, return_id);
