-- V17: Templates / Fillable Forms module.
--
-- Two new tenant-scoped tables:
--   - templates             : reusable fillable form definitions (quotation,
--                             invoice, receipt, contracts, PoA …).
--   - generated_documents   : a concrete document rendered from a template,
--                             optionally tied to a client / case.
--
-- Multi-tenancy: every row carries company_id and is filtered by the existing
-- Hibernate tenantFilter (see Person / CaseFile entities). The DB also FKs
-- company_id back to companies(id) so cross-tenant rows are impossible at the
-- storage level.

CREATE TABLE templates (
    id          UUID         PRIMARY KEY,
    company_id  UUID         NOT NULL REFERENCES companies(id),
    name        VARCHAR(200) NOT NULL,
    type        VARCHAR(40)  NOT NULL,
    content     TEXT         NOT NULL,
    is_default  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_templates_type CHECK (type IN (
        'QUOTATION',
        'INVOICE',
        'RECEIPT',
        'LEGAL_SERVICE_CONTRACT',
        'RETAINER_AGREEMENT',
        'POWER_OF_ATTORNEY'
    ))
);

CREATE INDEX idx_templates_company_id ON templates (company_id);
CREATE INDEX idx_templates_type       ON templates (type);

CREATE TABLE generated_documents (
    id                UUID         PRIMARY KEY,
    company_id        UUID         NOT NULL REFERENCES companies(id),
    template_id       UUID         NOT NULL REFERENCES templates(id),
    client_id         UUID         NULL     REFERENCES persons(id),
    case_id           UUID         NULL     REFERENCES cases(id),
    title             VARCHAR(250) NOT NULL,
    status            VARCHAR(30)  NOT NULL DEFAULT 'DRAFT',
    rendered_content  TEXT         NOT NULL,
    created_at        TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_generated_documents_status CHECK (status IN (
        'DRAFT',
        'UNDER_REVIEW',
        'APPROVED',
        'SENT'
    ))
);

CREATE INDEX idx_generated_documents_company_id ON generated_documents (company_id);
CREATE INDEX idx_generated_documents_status     ON generated_documents (status);
