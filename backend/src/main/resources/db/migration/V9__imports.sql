-- Importação de dados (CSV/XLSX): UPLOAD → MAPEAMENTO → VALIDAÇÃO → PREVIEW → CONFIRMAÇÃO → RELATÓRIO.
-- Linhas ficam persistidas para permitir processamento assíncrono no futuro.

CREATE TABLE import_jobs (
    id              UUID          NOT NULL,
    tenant_id       UUID          NOT NULL,
    entity_type     VARCHAR(20)   NOT NULL,
    file_name       VARCHAR(200)  NOT NULL,
    file_type       VARCHAR(10)   NOT NULL,
    status          VARCHAR(20)   NOT NULL,
    columns_json    VARCHAR(8000) NOT NULL,
    mapping_json    VARCHAR(8000),
    total_rows      INTEGER       NOT NULL DEFAULT 0,
    valid_rows      INTEGER       NOT NULL DEFAULT 0,
    invalid_rows    INTEGER       NOT NULL DEFAULT 0,
    created_count   INTEGER       NOT NULL DEFAULT 0,
    updated_count   INTEGER       NOT NULL DEFAULT 0,
    skipped_count   INTEGER       NOT NULL DEFAULT 0,
    created_by      UUID,
    completed_at    TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    version         BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_import_jobs PRIMARY KEY (id),
    CONSTRAINT uk_import_jobs_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_import_jobs_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
);
CREATE INDEX ix_import_jobs_tenant_created ON import_jobs (tenant_id, created_at);

CREATE TABLE import_job_rows (
    id              UUID           NOT NULL,
    tenant_id       UUID           NOT NULL,
    job_id          UUID           NOT NULL,
    row_number      INTEGER        NOT NULL,
    data_json       VARCHAR(20000) NOT NULL,
    errors_json     VARCHAR(4000),
    status          VARCHAR(20)    NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_import_job_rows PRIMARY KEY (id),
    CONSTRAINT fk_import_job_rows_job FOREIGN KEY (tenant_id, job_id) REFERENCES import_jobs (tenant_id, id) ON DELETE CASCADE
);
CREATE INDEX ix_import_job_rows_job ON import_job_rows (tenant_id, job_id, row_number);
