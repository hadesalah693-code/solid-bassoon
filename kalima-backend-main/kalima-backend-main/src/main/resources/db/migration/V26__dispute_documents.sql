-- Dispute-scoped file attachments (optional link to dispute_stages).
CREATE TABLE dispute_documents (
    id                 UUID PRIMARY KEY,
    company_id         UUID         NOT NULL REFERENCES companies (id),
    dispute_id         UUID         NOT NULL REFERENCES disputes (id) ON DELETE CASCADE,
    stage_id           UUID         REFERENCES dispute_stages (id) ON DELETE SET NULL,
    uploaded_by        UUID         NOT NULL REFERENCES persons (id),
    original_filename  VARCHAR(255) NOT NULL,
    stored_filename    VARCHAR(255) NOT NULL,
    content_type       VARCHAR(150) NOT NULL,
    size_bytes         BIGINT       NOT NULL,
    category           VARCHAR(40)  NOT NULL,
    visibility         VARCHAR(30)  NOT NULL,
    description        TEXT,
    created_at         TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_dispute_documents_dispute_created
    ON dispute_documents (company_id, dispute_id, created_at DESC);

CREATE INDEX idx_dispute_documents_stage
    ON dispute_documents (company_id, dispute_id, stage_id);
