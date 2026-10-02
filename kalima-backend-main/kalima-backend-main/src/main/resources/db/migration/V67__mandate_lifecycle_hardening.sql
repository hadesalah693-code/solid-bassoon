ALTER TABLE mandates
    ADD COLUMN next_hearing_id UUID REFERENCES hearings (id),
    ADD COLUMN deleted_at TIMESTAMP;

CREATE INDEX idx_mandates_company_next_hearing
    ON mandates (company_id, next_hearing_id)
    WHERE next_hearing_id IS NOT NULL;
CREATE INDEX idx_mandates_company_active_session
    ON mandates (company_id, session_date)
    WHERE deleted_at IS NULL;