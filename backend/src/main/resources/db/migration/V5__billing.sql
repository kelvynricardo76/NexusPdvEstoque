-- BILLING: cobrança da assinatura (tenant → Nexus Development).
-- Domínio totalmente separado do FINANCEIRO operacional do cliente.

CREATE TABLE subscription_invoices (
    id                  UUID          NOT NULL,
    tenant_id           UUID          NOT NULL,
    subscription_id     UUID          NOT NULL,
    description         VARCHAR(200)  NOT NULL,
    amount              NUMERIC(19,2) NOT NULL,
    due_date            DATE          NOT NULL,
    period_start        DATE,
    period_end          DATE,
    status              VARCHAR(20)   NOT NULL,
    paid_at             TIMESTAMP WITH TIME ZONE,
    billing_provider    VARCHAR(30)   NOT NULL,
    external_reference  VARCHAR(120),
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    version             BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_subscription_invoices PRIMARY KEY (id),
    CONSTRAINT fk_subscription_invoices_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_subscription_invoices_subscription FOREIGN KEY (subscription_id) REFERENCES tenant_subscriptions (id),
    CONSTRAINT uk_subscription_invoices_external UNIQUE (billing_provider, external_reference)
);
CREATE INDEX ix_subscription_invoices_tenant_due ON subscription_invoices (tenant_id, due_date);
CREATE INDEX ix_subscription_invoices_status ON subscription_invoices (status);

-- Idempotência de webhooks: cada evento do provedor é processado no máximo uma vez.
CREATE TABLE billing_webhook_events (
    id              UUID         NOT NULL,
    provider        VARCHAR(30)  NOT NULL,
    event_id        VARCHAR(120) NOT NULL,
    event_type      VARCHAR(60),
    received_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_billing_webhook_events PRIMARY KEY (id),
    CONSTRAINT uk_billing_webhook_events UNIQUE (provider, event_id)
);

INSERT INTO app_metadata (meta_key, meta_value, updated_at) VALUES
('platform.default_trial_days', '14', CURRENT_TIMESTAMP),
('platform.past_due_grace_days', '7', CURRENT_TIMESTAMP);
