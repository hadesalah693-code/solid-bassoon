-- Full-text search support for OCR records (title + extracted/edited text).

CREATE INDEX idx_ocr_records_fts
    ON ocr_records
    USING GIN (
        to_tsvector(
            'simple',
            coalesce(title, '') || ' ' ||
            coalesce(edited_text, coalesce(extracted_text, ''))
        )
    );

CREATE INDEX idx_ocr_records_company_status_created
    ON ocr_records(company_id, status, created_at DESC);

CREATE INDEX idx_ocr_records_company_processing_completed
    ON ocr_records(company_id, processing_completed_at DESC)
    WHERE processing_completed_at IS NOT NULL;
