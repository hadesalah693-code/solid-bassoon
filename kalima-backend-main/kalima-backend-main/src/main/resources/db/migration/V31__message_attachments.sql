CREATE TABLE IF NOT EXISTS message_attachments (
    id                UUID         NOT NULL DEFAULT gen_random_uuid(),
    company_id        UUID         NOT NULL,
    case_id           UUID         NOT NULL,
    uploaded_by_id    UUID         NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    stored_filename   VARCHAR(255) NOT NULL,
    content_type      VARCHAR(150) NOT NULL,
    size_bytes        BIGINT       NOT NULL,
    created_at        TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_message_attachments PRIMARY KEY (id),
    CONSTRAINT fk_ma_company     FOREIGN KEY (company_id)     REFERENCES companies(id),
    CONSTRAINT fk_ma_case        FOREIGN KEY (case_id)        REFERENCES cases(id),
    CONSTRAINT fk_ma_uploaded_by FOREIGN KEY (uploaded_by_id) REFERENCES persons(id),
    CONSTRAINT uq_ma_stored_filename UNIQUE (stored_filename)
);

CREATE INDEX IF NOT EXISTS idx_ma_company_case ON message_attachments(company_id, case_id);
CREATE INDEX IF NOT EXISTS idx_ma_uploaded_by  ON message_attachments(uploaded_by_id);
