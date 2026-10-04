-- Núcleo SaaS: plataforma Nexus, catálogo comercial (planos, features, limites) e tenants.
-- Nenhuma destas tabelas é "de domínio do tenant"; são administradas pelo SUPER_ADMIN.

CREATE TABLE platform_admins (
    id              UUID         NOT NULL,
    name            VARCHAR(120) NOT NULL,
    email           VARCHAR(254) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    last_login_at   TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_platform_admins PRIMARY KEY (id),
    CONSTRAINT uk_platform_admins_email UNIQUE (email)
);

-- Catálogo de funcionalidades. O código é contrato com o backend (FeatureCode).
CREATE TABLE features (
    code            VARCHAR(50)  NOT NULL,
    name            VARCHAR(100) NOT NULL,
    description     VARCHAR(300),
    display_order   INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT pk_features PRIMARY KEY (code)
);

-- Catálogo de limites. default_value = -1 significa ilimitado.
CREATE TABLE limit_definitions (
    code            VARCHAR(50)  NOT NULL,
    name            VARCHAR(100) NOT NULL,
    description     VARCHAR(300),
    unit            VARCHAR(20),
    default_value   BIGINT       NOT NULL,
    display_order   INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT pk_limit_definitions PRIMARY KEY (code)
);

CREATE TABLE plans (
    id              UUID          NOT NULL,
    code            VARCHAR(40)   NOT NULL,
    name            VARCHAR(80)   NOT NULL,
    description     VARCHAR(500),
    monthly_price   NUMERIC(19,2) NOT NULL,
    annual_price    NUMERIC(19,2) NOT NULL,
    active          BOOLEAN       NOT NULL,
    display_order   INTEGER       NOT NULL DEFAULT 0,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    version         BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_plans PRIMARY KEY (id),
    CONSTRAINT uk_plans_code UNIQUE (code)
);

CREATE TABLE plan_features (
    plan_id         UUID        NOT NULL,
    feature_code    VARCHAR(50) NOT NULL,
    CONSTRAINT pk_plan_features PRIMARY KEY (plan_id, feature_code),
    CONSTRAINT fk_plan_features_plan FOREIGN KEY (plan_id) REFERENCES plans (id) ON DELETE CASCADE,
    CONSTRAINT fk_plan_features_feature FOREIGN KEY (feature_code) REFERENCES features (code)
);

-- limit_value = -1 significa ilimitado.
CREATE TABLE plan_limits (
    plan_id         UUID        NOT NULL,
    limit_code      VARCHAR(50) NOT NULL,
    limit_value     BIGINT      NOT NULL,
    CONSTRAINT pk_plan_limits PRIMARY KEY (plan_id, limit_code),
    CONSTRAINT fk_plan_limits_plan FOREIGN KEY (plan_id) REFERENCES plans (id) ON DELETE CASCADE,
    CONSTRAINT fk_plan_limits_limit FOREIGN KEY (limit_code) REFERENCES limit_definitions (code)
);

CREATE TABLE tenants (
    id              UUID         NOT NULL,
    name            VARCHAR(150) NOT NULL,
    trade_name      VARCHAR(150),
    document        VARCHAR(20),
    email           VARCHAR(254),
    phone           VARCHAR(30),
    status          VARCHAR(20)  NOT NULL,
    status_reason   VARCHAR(300),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_tenants PRIMARY KEY (id)
);
CREATE INDEX ix_tenants_status ON tenants (status);
CREATE INDEX ix_tenants_created_at ON tenants (created_at);

CREATE TABLE tenant_settings (
    tenant_id             UUID         NOT NULL,
    company_name          VARCHAR(150) NOT NULL,
    trade_name            VARCHAR(150),
    logo_data_url         VARCHAR(400000),
    primary_color         VARCHAR(7),
    secondary_color       VARCHAR(7),
    document              VARCHAR(20),
    phone                 VARCHAR(30),
    email                 VARCHAR(254),
    address               VARCHAR(300),
    timezone              VARCHAR(60)  NOT NULL,
    currency              VARCHAR(3)   NOT NULL,
    allow_negative_stock  BOOLEAN      NOT NULL DEFAULT FALSE,
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    version               BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_tenant_settings PRIMARY KEY (tenant_id),
    CONSTRAINT fk_tenant_settings_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
);

CREATE TABLE tenant_subscriptions (
    id                    UUID        NOT NULL,
    tenant_id             UUID        NOT NULL,
    plan_id               UUID        NOT NULL,
    status                VARCHAR(20) NOT NULL,
    billing_cycle         VARCHAR(10) NOT NULL,
    start_date            DATE        NOT NULL,
    trial_end_date        DATE,
    current_period_start  DATE,
    current_period_end    DATE,
    cancel_at_period_end  BOOLEAN     NOT NULL DEFAULT FALSE,
    billing_provider      VARCHAR(30) NOT NULL,
    external_reference    VARCHAR(120),
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    version               BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT pk_tenant_subscriptions PRIMARY KEY (id),
    CONSTRAINT uk_tenant_subscriptions_tenant UNIQUE (tenant_id),
    CONSTRAINT fk_tenant_subscriptions_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_tenant_subscriptions_plan FOREIGN KEY (plan_id) REFERENCES plans (id)
);
CREATE INDEX ix_tenant_subscriptions_status ON tenant_subscriptions (status);
CREATE INDEX ix_tenant_subscriptions_plan ON tenant_subscriptions (plan_id);

-- Exceções comerciais concedidas pela Nexus a um tenant específico.
CREATE TABLE tenant_feature_overrides (
    tenant_id       UUID        NOT NULL,
    feature_code    VARCHAR(50) NOT NULL,
    enabled         BOOLEAN     NOT NULL,
    CONSTRAINT pk_tenant_feature_overrides PRIMARY KEY (tenant_id, feature_code),
    CONSTRAINT fk_tfo_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_tfo_feature FOREIGN KEY (feature_code) REFERENCES features (code)
);

CREATE TABLE tenant_limit_overrides (
    tenant_id       UUID        NOT NULL,
    limit_code      VARCHAR(50) NOT NULL,
    limit_value     BIGINT      NOT NULL,
    CONSTRAINT pk_tenant_limit_overrides PRIMARY KEY (tenant_id, limit_code),
    CONSTRAINT fk_tlo_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_tlo_limit FOREIGN KEY (limit_code) REFERENCES limit_definitions (code)
);

-- Sequências por tenant (ex.: número da venda). Linha travada com FOR UPDATE ao incrementar.
CREATE TABLE tenant_counters (
    tenant_id       UUID        NOT NULL,
    counter_name    VARCHAR(40) NOT NULL,
    current_value   BIGINT      NOT NULL,
    CONSTRAINT pk_tenant_counters PRIMARY KEY (tenant_id, counter_name),
    CONSTRAINT fk_tenant_counters_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
);

-- ---------------------------------------------------------------------------
-- Dados de referência (editáveis pelo Super Admin; não são regras hardcoded).
-- ---------------------------------------------------------------------------

INSERT INTO features (code, name, description, display_order) VALUES
('PDV',                'PDV',                    'Frente de caixa',                                   10),
('SALES',              'Vendas',                 'Consulta e gestão de vendas',                        20),
('PRODUCTS',           'Produtos',               'Cadastro de produtos',                               30),
('CATEGORIES',         'Categorias',             'Categorias de produtos',                             40),
('STOCK',              'Estoque',                'Controle de estoque e movimentações',                50),
('CUSTOMERS',          'Clientes',               'Cadastro e histórico de clientes',                   60),
('SUPPLIERS',          'Fornecedores',           'Cadastro de fornecedores',                           70),
('FINANCIAL',          'Financeiro',             'Contas a pagar e a receber',                         80),
('REPORTS',            'Relatórios',             'Relatórios básicos',                                 90),
('ADVANCED_REPORTS',   'Relatórios avançados',   'Relatórios analíticos avançados',                   100),
('DATA_IMPORT',        'Importação de dados',    'Importação via CSV/XLSX',                           110),
('CSV_EXPORT',         'Exportação CSV',         'Exportação de relatórios em CSV',                   120),
('PDF_EXPORT',         'Exportação PDF',         'Exportação de relatórios em PDF',                   130),
('ADVANCED_DASHBOARD', 'Dashboard avançado',     'Indicadores avançados no dashboard',                140),
('MULTI_BRANCH',       'Multiempresa/filiais',   'Gestão de múltiplas unidades',                      150),
('BATCH_CONTROL',      'Controle de lotes',      'Rastreabilidade por lote',                          160),
('EXPIRATION_CONTROL', 'Controle de validade',   'Controle de vencimento de produtos',                170),
('SCALE_INTEGRATION',  'Integração com balança', 'Leitura de etiquetas de balança',                   180);

INSERT INTO limit_definitions (code, name, description, unit, default_value, display_order) VALUES
('MAX_USERS',           'Usuários',               'Quantidade máxima de usuários ativos',        'usuários',  1,   10),
('MAX_PRODUCTS',        'Produtos',               'Quantidade máxima de produtos cadastrados',   'produtos',  100, 20),
('MAX_BRANCHES',        'Filiais',                'Quantidade máxima de unidades',               'filiais',   1,   30),
('MAX_MONTHLY_SALES',   'Vendas por mês',         'Quantidade máxima de vendas no mês corrente', 'vendas',    -1,  40),
('REPORT_HISTORY_DAYS', 'Histórico de relatórios','Dias de histórico disponíveis em relatórios', 'dias',      30,  50),
('STORAGE_LIMIT_MB',    'Armazenamento',          'Espaço de armazenamento de arquivos',         'MB',        50,  60);

INSERT INTO plans (id, code, name, description, monthly_price, annual_price, active, display_order, created_at, updated_at, version) VALUES
('0f5d6c1e-0000-4000-8000-000000000001', 'BASIC', 'Básico', 'PDV, produtos, estoque, clientes e relatórios básicos.', 79.90, 799.00, TRUE, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
('0f5d6c1e-0000-4000-8000-000000000002', 'PLUS',  'Plus',   'Tudo do Básico + fornecedores, financeiro, relatórios avançados, importação e exportação.', 149.90, 1499.00, TRUE, 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

INSERT INTO plan_features (plan_id, feature_code) VALUES
('0f5d6c1e-0000-4000-8000-000000000001', 'PDV'),
('0f5d6c1e-0000-4000-8000-000000000001', 'SALES'),
('0f5d6c1e-0000-4000-8000-000000000001', 'PRODUCTS'),
('0f5d6c1e-0000-4000-8000-000000000001', 'CATEGORIES'),
('0f5d6c1e-0000-4000-8000-000000000001', 'STOCK'),
('0f5d6c1e-0000-4000-8000-000000000001', 'CUSTOMERS'),
('0f5d6c1e-0000-4000-8000-000000000001', 'REPORTS'),
('0f5d6c1e-0000-4000-8000-000000000002', 'PDV'),
('0f5d6c1e-0000-4000-8000-000000000002', 'SALES'),
('0f5d6c1e-0000-4000-8000-000000000002', 'PRODUCTS'),
('0f5d6c1e-0000-4000-8000-000000000002', 'CATEGORIES'),
('0f5d6c1e-0000-4000-8000-000000000002', 'STOCK'),
('0f5d6c1e-0000-4000-8000-000000000002', 'CUSTOMERS'),
('0f5d6c1e-0000-4000-8000-000000000002', 'REPORTS'),
('0f5d6c1e-0000-4000-8000-000000000002', 'SUPPLIERS'),
('0f5d6c1e-0000-4000-8000-000000000002', 'FINANCIAL'),
('0f5d6c1e-0000-4000-8000-000000000002', 'ADVANCED_REPORTS'),
('0f5d6c1e-0000-4000-8000-000000000002', 'DATA_IMPORT'),
('0f5d6c1e-0000-4000-8000-000000000002', 'CSV_EXPORT'),
('0f5d6c1e-0000-4000-8000-000000000002', 'PDF_EXPORT'),
('0f5d6c1e-0000-4000-8000-000000000002', 'ADVANCED_DASHBOARD');

INSERT INTO plan_limits (plan_id, limit_code, limit_value) VALUES
('0f5d6c1e-0000-4000-8000-000000000001', 'MAX_USERS',           3),
('0f5d6c1e-0000-4000-8000-000000000001', 'MAX_PRODUCTS',        1000),
('0f5d6c1e-0000-4000-8000-000000000001', 'MAX_BRANCHES',        1),
('0f5d6c1e-0000-4000-8000-000000000001', 'MAX_MONTHLY_SALES',   -1),
('0f5d6c1e-0000-4000-8000-000000000001', 'REPORT_HISTORY_DAYS', 90),
('0f5d6c1e-0000-4000-8000-000000000001', 'STORAGE_LIMIT_MB',    100),
('0f5d6c1e-0000-4000-8000-000000000002', 'MAX_USERS',           10),
('0f5d6c1e-0000-4000-8000-000000000002', 'MAX_PRODUCTS',        10000),
('0f5d6c1e-0000-4000-8000-000000000002', 'MAX_BRANCHES',        3),
('0f5d6c1e-0000-4000-8000-000000000002', 'MAX_MONTHLY_SALES',   -1),
('0f5d6c1e-0000-4000-8000-000000000002', 'REPORT_HISTORY_DAYS', 730),
('0f5d6c1e-0000-4000-8000-000000000002', 'STORAGE_LIMIT_MB',    1000);
