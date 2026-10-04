-- Identidade dos tenants: usuários, cargos (RBAC) e permissões granulares.

-- Catálogo de permissões. O código é contrato com o backend (enum Permission).
-- feature_code: feature que o tenant precisa possuir para a permissão ter efeito.
CREATE TABLE permissions (
    code            VARCHAR(50)  NOT NULL,
    module          VARCHAR(30)  NOT NULL,
    name            VARCHAR(100) NOT NULL,
    feature_code    VARCHAR(50),
    display_order   INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT pk_permissions PRIMARY KEY (code),
    CONSTRAINT fk_permissions_feature FOREIGN KEY (feature_code) REFERENCES features (code)
);

CREATE TABLE roles (
    id              UUID         NOT NULL,
    tenant_id       UUID         NOT NULL,
    code            VARCHAR(40),
    name            VARCHAR(80)  NOT NULL,
    description     VARCHAR(300),
    system_role     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_roles PRIMARY KEY (id),
    CONSTRAINT uk_roles_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_roles_tenant_name UNIQUE (tenant_id, name),
    CONSTRAINT uk_roles_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT fk_roles_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
);

CREATE TABLE role_permissions (
    role_id          UUID        NOT NULL,
    permission_code  VARCHAR(50) NOT NULL,
    CONSTRAINT pk_role_permissions PRIMARY KEY (role_id, permission_code),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE,
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_code) REFERENCES permissions (code)
);

-- E-mail é único globalmente: o login não pede a empresa.
CREATE TABLE users (
    id              UUID         NOT NULL,
    tenant_id       UUID         NOT NULL,
    role_id         UUID         NOT NULL,
    name            VARCHAR(120) NOT NULL,
    email           VARCHAR(254) NOT NULL,
    phone           VARCHAR(30),
    password_hash   VARCHAR(255) NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    last_login_at   TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_users_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    -- FK composta: o cargo precisa pertencer ao mesmo tenant do usuário.
    CONSTRAINT fk_users_role FOREIGN KEY (tenant_id, role_id) REFERENCES roles (tenant_id, id)
);
CREATE INDEX ix_users_tenant_status ON users (tenant_id, status);

-- ALLOW concede além do cargo; DENY remove (DENY sempre vence).
CREATE TABLE user_permission_overrides (
    user_id          UUID        NOT NULL,
    permission_code  VARCHAR(50) NOT NULL,
    effect           VARCHAR(5)  NOT NULL,
    CONSTRAINT pk_user_permission_overrides PRIMARY KEY (user_id, permission_code),
    CONSTRAINT fk_upo_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_upo_permission FOREIGN KEY (permission_code) REFERENCES permissions (code),
    CONSTRAINT ck_upo_effect CHECK (effect IN ('ALLOW', 'DENY'))
);

-- Somente o hash SHA-256 do token é armazenado.
CREATE TABLE password_reset_tokens (
    id              UUID        NOT NULL,
    user_id         UUID        NOT NULL,
    token_hash      VARCHAR(64) NOT NULL,
    expires_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at         TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_password_reset_tokens PRIMARY KEY (id),
    CONSTRAINT uk_password_reset_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_password_reset_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

INSERT INTO permissions (code, module, name, feature_code, display_order) VALUES
('DASHBOARD_VIEW',          'DASHBOARD',  'Visualizar dashboard',           NULL,               10),
('PDV_ACCESS',              'PDV',        'Acessar o PDV',                  'PDV',              20),
('SALE_READ',               'SALES',      'Visualizar vendas',              'SALES',            30),
('SALE_CREATE',             'SALES',      'Registrar vendas',               'PDV',              31),
('SALE_CANCEL',             'SALES',      'Cancelar venda',                 'SALES',            32),
('SALE_DISCOUNT',           'SALES',      'Aplicar desconto',               'SALES',            33),
('SALE_REFUND',             'SALES',      'Registrar devolução',            'SALES',            34),
('PRODUCT_READ',            'PRODUCTS',   'Visualizar produtos',            'PRODUCTS',         40),
('PRODUCT_CREATE',          'PRODUCTS',   'Cadastrar produtos',             'PRODUCTS',         41),
('PRODUCT_UPDATE',          'PRODUCTS',   'Editar produtos',                'PRODUCTS',         42),
('PRODUCT_DISABLE',         'PRODUCTS',   'Desativar produtos',             'PRODUCTS',         43),
('PRODUCT_COST_VIEW',       'PRODUCTS',   'Visualizar custo',               'PRODUCTS',         44),
('CATEGORY_READ',           'CATEGORIES', 'Visualizar categorias',          'CATEGORIES',       50),
('CATEGORY_CREATE',         'CATEGORIES', 'Cadastrar categorias',           'CATEGORIES',       51),
('CATEGORY_UPDATE',         'CATEGORIES', 'Editar categorias',              'CATEGORIES',       52),
('CATEGORY_DISABLE',        'CATEGORIES', 'Desativar categorias',           'CATEGORIES',       53),
('STOCK_READ',              'STOCK',      'Visualizar estoque',             'STOCK',            60),
('STOCK_ENTRY',             'STOCK',      'Registrar entrada',              'STOCK',            61),
('STOCK_ADJUST',            'STOCK',      'Ajustar estoque',                'STOCK',            62),
('STOCK_MOVEMENT_READ',     'STOCK',      'Visualizar movimentações',       'STOCK',            63),
('CUSTOMER_READ',           'CUSTOMERS',  'Visualizar clientes',            'CUSTOMERS',        70),
('CUSTOMER_CREATE',         'CUSTOMERS',  'Cadastrar clientes',             'CUSTOMERS',        71),
('CUSTOMER_UPDATE',         'CUSTOMERS',  'Editar clientes',                'CUSTOMERS',        72),
('CUSTOMER_DISABLE',        'CUSTOMERS',  'Desativar clientes',             'CUSTOMERS',        73),
('SUPPLIER_READ',           'SUPPLIERS',  'Visualizar fornecedores',        'SUPPLIERS',        80),
('SUPPLIER_CREATE',         'SUPPLIERS',  'Cadastrar fornecedores',         'SUPPLIERS',        81),
('SUPPLIER_UPDATE',         'SUPPLIERS',  'Editar fornecedores',            'SUPPLIERS',        82),
('SUPPLIER_DISABLE',        'SUPPLIERS',  'Desativar fornecedores',         'SUPPLIERS',        83),
('FINANCIAL_READ',          'FINANCIAL',  'Visualizar financeiro',          'FINANCIAL',        90),
('FINANCIAL_CREATE',        'FINANCIAL',  'Lançar contas',                  'FINANCIAL',        91),
('FINANCIAL_UPDATE',        'FINANCIAL',  'Editar e baixar contas',         'FINANCIAL',        92),
('FINANCIAL_CANCEL',        'FINANCIAL',  'Cancelar contas',                'FINANCIAL',        93),
('REPORT_BASIC_READ',       'REPORTS',    'Relatórios básicos',             'REPORTS',         100),
('REPORT_ADVANCED_READ',    'REPORTS',    'Relatórios avançados',           'ADVANCED_REPORTS',101),
('REPORT_EXPORT',           'REPORTS',    'Exportar relatórios',            'REPORTS',         102),
('IMPORT_EXECUTE',          'IMPORT',     'Importar dados',                 'DATA_IMPORT',     110),
('USER_READ',               'USERS',      'Visualizar usuários',            NULL,              120),
('USER_CREATE',             'USERS',      'Cadastrar usuários',             NULL,              121),
('USER_UPDATE',             'USERS',      'Editar usuários',                NULL,              122),
('USER_DISABLE',            'USERS',      'Desativar usuários',             NULL,              123),
('USER_PERMISSION_MANAGE',  'USERS',      'Gerenciar cargos e permissões',  NULL,              124),
('AUDIT_READ',              'AUDIT',      'Visualizar auditoria',           NULL,              130),
('SETTINGS_MANAGE',         'SETTINGS',   'Gerenciar configurações',        NULL,              140);
