-- Vendas, itens e pagamentos. Vendas concluídas nunca são apagadas fisicamente (cancelamento é status).

CREATE TABLE sales (
    id               UUID          NOT NULL,
    tenant_id        UUID          NOT NULL,
    number           BIGINT        NOT NULL,
    customer_id      UUID,
    operator_id      UUID          NOT NULL,
    subtotal         NUMERIC(19,2) NOT NULL,
    discount         NUMERIC(19,2) NOT NULL,
    total            NUMERIC(19,2) NOT NULL,
    change_amount    NUMERIC(19,2) NOT NULL DEFAULT 0,
    status           VARCHAR(20)   NOT NULL,
    idempotency_key  VARCHAR(80),
    cancel_reason    VARCHAR(300),
    canceled_at      TIMESTAMP WITH TIME ZONE,
    canceled_by      UUID,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    version          BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_sales PRIMARY KEY (id),
    CONSTRAINT uk_sales_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_sales_tenant_number UNIQUE (tenant_id, number),
    CONSTRAINT uk_sales_tenant_idempotency UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT fk_sales_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_sales_customer FOREIGN KEY (tenant_id, customer_id) REFERENCES customers (tenant_id, id),
    CONSTRAINT fk_sales_operator FOREIGN KEY (tenant_id, operator_id) REFERENCES users (tenant_id, id),
    CONSTRAINT fk_sales_canceled_by FOREIGN KEY (tenant_id, canceled_by) REFERENCES users (tenant_id, id),
    CONSTRAINT ck_sales_amounts CHECK (subtotal >= 0 AND discount >= 0 AND total >= 0 AND change_amount >= 0)
);
CREATE INDEX ix_sales_tenant_created ON sales (tenant_id, created_at);
CREATE INDEX ix_sales_tenant_status ON sales (tenant_id, status);
CREATE INDEX ix_sales_tenant_customer ON sales (tenant_id, customer_id);

CREATE TABLE sale_items (
    id                    UUID          NOT NULL,
    tenant_id             UUID          NOT NULL,
    sale_id               UUID          NOT NULL,
    product_id            UUID          NOT NULL,
    line_number           INTEGER       NOT NULL,
    description_snapshot  VARCHAR(150)  NOT NULL,
    sku_snapshot          VARCHAR(60),
    quantity              NUMERIC(19,3) NOT NULL,
    unit_price            NUMERIC(19,2) NOT NULL,
    cost_price_snapshot   NUMERIC(19,2) NOT NULL,
    discount              NUMERIC(19,2) NOT NULL,
    total                 NUMERIC(19,2) NOT NULL,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_sale_items PRIMARY KEY (id),
    CONSTRAINT fk_sale_items_sale FOREIGN KEY (tenant_id, sale_id) REFERENCES sales (tenant_id, id),
    CONSTRAINT fk_sale_items_product FOREIGN KEY (tenant_id, product_id) REFERENCES products (tenant_id, id),
    CONSTRAINT ck_sale_items_values CHECK (quantity > 0 AND unit_price >= 0 AND discount >= 0 AND total >= 0)
);
CREATE INDEX ix_sale_items_sale ON sale_items (tenant_id, sale_id);
CREATE INDEX ix_sale_items_product ON sale_items (tenant_id, product_id);

-- Estrutura preparada para pagamento dividido (várias formas por venda).
CREATE TABLE payments (
    id              UUID          NOT NULL,
    tenant_id       UUID          NOT NULL,
    sale_id         UUID          NOT NULL,
    method          VARCHAR(20)   NOT NULL,
    amount          NUMERIC(19,2) NOT NULL,
    status          VARCHAR(20)   NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_payments PRIMARY KEY (id),
    CONSTRAINT fk_payments_sale FOREIGN KEY (tenant_id, sale_id) REFERENCES sales (tenant_id, id),
    CONSTRAINT ck_payments_amount CHECK (amount > 0)
);
CREATE INDEX ix_payments_sale ON payments (tenant_id, sale_id);
