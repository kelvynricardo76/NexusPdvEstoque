-- Trilha de auditoria. tenant_id nulo = ação da plataforma (Super Admin).
-- Nunca armazena senhas, tokens, dados de cartão ou segredos (metadados são sanitizados).
CREATE TABLE audit_logs (
    id              UUID          NOT NULL,
    tenant_id       UUID,
    actor_type      VARCHAR(20)   NOT NULL,
    actor_id        UUID,
    actor_name      VARCHAR(120),
    action          VARCHAR(40)   NOT NULL,
    entity          VARCHAR(60),
    entity_id       VARCHAR(64),
    ip              VARCHAR(64),
    endpoint        VARCHAR(200),
    metadata        VARCHAR(4000),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_audit_logs PRIMARY KEY (id)
);
CREATE INDEX ix_audit_logs_tenant_created ON audit_logs (tenant_id, created_at);
CREATE INDEX ix_audit_logs_created ON audit_logs (created_at);
CREATE INDEX ix_audit_logs_action ON audit_logs (action);
