-- OCR module: standalone scanned-document records with polymorphic relations.

CREATE TABLE ocr_records (
    id                       UUID PRIMARY KEY,
    company_id               UUID          NOT NULL REFERENCES companies(id),
    title                    VARCHAR(250)  NOT NULL,
    original_filename        VARCHAR(255)  NOT NULL,
    stored_filename          VARCHAR(255)  NOT NULL,
    content_type             VARCHAR(150)  NOT NULL,
    size_bytes               BIGINT        NOT NULL,
    document_type            VARCHAR(40)   NOT NULL DEFAULT 'OTHER',
    extracted_text           TEXT,
    edited_text              TEXT,
    language                 VARCHAR(20),
    confidence_score         NUMERIC(5, 4),
    status                   VARCHAR(30)   NOT NULL DEFAULT 'PENDING',
    error_message            TEXT,
    provider                 VARCHAR(40),
    uploaded_by              UUID          NOT NULL REFERENCES persons(id),
    processing_started_at    TIMESTAMP,
    processing_completed_at  TIMESTAMP,
    ai_metadata              JSONB,
    created_at               TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_ocr_records_status CHECK (
        status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED')
    ),
    CONSTRAINT chk_ocr_records_document_type CHECK (
        document_type IN ('SCAN', 'CONTRACT', 'COURT_FILING', 'ID', 'OTHER')
    )
);

CREATE INDEX idx_ocr_records_company_created
    ON ocr_records(company_id, created_at DESC);

CREATE INDEX idx_ocr_records_company_status
    ON ocr_records(company_id, status);

CREATE INDEX idx_ocr_records_company_uploaded_by
    ON ocr_records(company_id, uploaded_by);

CREATE INDEX idx_ocr_records_company_document_type
    ON ocr_records(company_id, document_type);

CREATE TABLE ocr_relations (
    id              UUID PRIMARY KEY,
    company_id      UUID         NOT NULL REFERENCES companies(id),
    ocr_record_id   UUID         NOT NULL REFERENCES ocr_records(id) ON DELETE CASCADE,
    relation_type   VARCHAR(30)  NOT NULL,
    relation_id     UUID         NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_ocr_relations UNIQUE (ocr_record_id, relation_type, relation_id),
    CONSTRAINT chk_ocr_relations_type CHECK (
        relation_type IN ('CASE', 'DISPUTE', 'CLIENT', 'TASK', 'DOCUMENT')
    )
);

CREATE INDEX idx_ocr_relations_record
    ON ocr_relations(ocr_record_id);

CREATE INDEX idx_ocr_relations_lookup
    ON ocr_relations(company_id, relation_type, relation_id);
