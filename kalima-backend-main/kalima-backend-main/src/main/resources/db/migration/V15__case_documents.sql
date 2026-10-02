CREATE TABLE case_documents (
    id                UUID PRIMARY KEY,
    company_id        UUID         NOT NULL REFERENCES companies(id),
    case_id           UUID         NOT NULL REFERENCES cases(id) ON DELETE CASCADE,
    uploaded_by       UUID         NOT NULL REFERENCES persons(id),
    original_filename VARCHAR(255) NOT NULL,
    stored_filename   VARCHAR(255) NOT NULL,
    content_type      VARCHAR(150) NOT NULL,
    size_bytes        BIGINT       NOT NULL,
    category          VARCHAR(40)  NOT NULL,
    visibility        VARCHAR(30)  NOT NULL,
    description       TEXT,
    created_at        TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_case_documents_company_case_created
    ON case_documents(company_id, case_id, created_at DESC);
CREATE INDEX idx_case_documents_case_category
    ON case_documents(case_id, category);
CREATE INDEX idx_case_documents_visibility
    ON case_documents(visibility);
