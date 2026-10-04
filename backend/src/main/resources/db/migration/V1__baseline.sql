-- Nexus PDV & Estoque — baseline do schema.
--
-- Convenções para todas as migrations:
--   * SQL portável entre H2 (MODE=PostgreSQL) e PostgreSQL — sem jsonb, índices parciais
--     ou funções exclusivas de um banco.
--   * Identificadores em snake_case, minúsculos.
--   * PKs UUID; tabelas de domínio possuem tenant_id NOT NULL.
--   * Dinheiro NUMERIC(19,2); quantidades NUMERIC(19,3); datas TIMESTAMP WITH TIME ZONE (UTC).
--   * Unicidades de domínio sempre incluem tenant_id quando aplicável.
--
-- As tabelas de domínio são introduzidas a partir da Fase 2 (tenants + autenticação).

CREATE TABLE app_metadata (
    meta_key    VARCHAR(100) NOT NULL,
    meta_value  VARCHAR(500) NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_app_metadata PRIMARY KEY (meta_key)
);

INSERT INTO app_metadata (meta_key, meta_value, updated_at)
VALUES ('schema.baseline', 'nexus-pdv', CURRENT_TIMESTAMP);
