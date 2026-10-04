-- Catálogo, estoque, clientes e fornecedores (dados do tenant).
-- FKs compostas (tenant_id, x_id) garantem no próprio banco que relacionamentos nunca cruzam tenants.

CREATE TABLE categories (
    id              UUID         NOT NULL,
    tenant_id       UUID         NOT NULL,
    name            VARCHAR(80)  NOT NULL,
    description     VARCHAR(300),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_categories PRIMARY KEY (id),
    CONSTRAINT uk_categories_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_categories_tenant_name UNIQUE (tenant_id, name),
    CONSTRAINT fk_categories_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
);

CREATE TABLE suppliers (
    id              UUID         NOT NULL,
    tenant_id       UUID         NOT NULL,
    legal_name      VARCHAR(150) NOT NULL,
    trade_name      VARCHAR(150),
    document        VARCHAR(20),
    phone           VARCHAR(30),
    email           VARCHAR(254),
    address         VARCHAR(300),
    notes           VARCHAR(1000),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_suppliers PRIMARY KEY (id),
    CONSTRAINT uk_suppliers_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_suppliers_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
);
CREATE INDEX ix_suppliers_tenant_name ON suppliers (tenant_id, legal_name);
CREATE INDEX ix_suppliers_tenant_document ON suppliers (tenant_id, document);

CREATE TABLE products (
    id              UUID          NOT NULL,
    tenant_id       UUID          NOT NULL,
    name            VARCHAR(150)  NOT NULL,
    description     VARCHAR(1000),
    sku             VARCHAR(60),
    barcode         VARCHAR(60),
    category_id     UUID,
    supplier_id     UUID,
    sale_price      NUMERIC(19,2) NOT NULL,
    cost_price      NUMERIC(19,2) NOT NULL DEFAULT 0,
    current_stock   NUMERIC(19,3) NOT NULL DEFAULT 0,
    minimum_stock   NUMERIC(19,3) NOT NULL DEFAULT 0,
    unit            VARCHAR(10)   NOT NULL,
    active          BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    version         BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_products PRIMARY KEY (id),
    CONSTRAINT uk_products_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_products_tenant_sku UNIQUE (tenant_id, sku),
    CONSTRAINT uk_products_tenant_barcode UNIQUE (tenant_id, barcode),
    CONSTRAINT fk_products_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_products_category FOREIGN KEY (tenant_id, category_id) REFERENCES categories (tenant_id, id),
    CONSTRAINT fk_products_supplier FOREIGN KEY (tenant_id, supplier_id) REFERENCES suppliers (tenant_id, id),
    CONSTRAINT ck_products_prices CHECK (sale_price >= 0 AND cost_price >= 0),
    CONSTRAINT ck_products_minimum CHECK (minimum_stock >= 0)
);
CREATE INDEX ix_products_tenant_name ON products (tenant_id, name);
CREATE INDEX ix_products_tenant_active ON products (tenant_id, active);
CREATE INDEX ix_products_tenant_category ON products (tenant_id, category_id);

-- Toda alteração de estoque gera um movimento (nunca se altera current_stock sem registro).
CREATE TABLE stock_movements (
    id              UUID          NOT NULL,
    tenant_id       UUID          NOT NULL,
    product_id      UUID          NOT NULL,
    type            VARCHAR(30)   NOT NULL,
    quantity        NUMERIC(19,3) NOT NULL,
    previous_stock  NUMERIC(19,3) NOT NULL,
    new_stock       NUMERIC(19,3) NOT NULL,
    reference_type  VARCHAR(30),
    reference_id    UUID,
    reason          VARCHAR(300),
    user_id         UUID,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_stock_movements PRIMARY KEY (id),
    CONSTRAINT fk_stock_movements_product FOREIGN KEY (tenant_id, product_id) REFERENCES products (tenant_id, id),
    CONSTRAINT fk_stock_movements_user FOREIGN KEY (tenant_id, user_id) REFERENCES users (tenant_id, id),
    CONSTRAINT ck_stock_movements_quantity CHECK (quantity > 0)
);
CREATE INDEX ix_stock_movements_tenant_product ON stock_movements (tenant_id, product_id, created_at);
CREATE INDEX ix_stock_movements_tenant_created ON stock_movements (tenant_id, created_at);
CREATE INDEX ix_stock_movements_reference ON stock_movements (tenant_id, reference_type, reference_id);

CREATE TABLE customers (
    id              UUID         NOT NULL,
    tenant_id       UUID         NOT NULL,
    name            VARCHAR(150) NOT NULL,
    document        VARCHAR(20),
    phone           VARCHAR(30),
    email           VARCHAR(254),
    address         VARCHAR(300),
    notes           VARCHAR(1000),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_customers PRIMARY KEY (id),
    CONSTRAINT uk_customers_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_customers_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
);
CREATE INDEX ix_customers_tenant_name ON customers (tenant_id, name);
CREATE INDEX ix_customers_tenant_document ON customers (tenant_id, document);
