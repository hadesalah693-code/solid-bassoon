ALTER TABLE hearings
    ADD COLUMN hearing_type VARCHAR(30) NOT NULL DEFAULT 'ORDINARY',
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    ADD COLUMN outcome_code VARCHAR(50),
    ADD COLUMN did_attend BOOLEAN,
    ADD COLUMN presiding_judge VARCHAR(200),
    ADD COLUMN previous_hearing_id UUID,
    ADD COLUMN duration_minutes INTEGER;

ALTER TABLE hearings
    ADD CONSTRAINT fk_hearings_previous_hearing
        FOREIGN KEY (previous_hearing_id) REFERENCES hearings (id),
    ADD CONSTRAINT chk_hearings_type
        CHECK (hearing_type IN ('ORDINARY', 'DEFENSE', 'EVIDENCE', 'EXPEDITED',
            'ARBITRATION', 'INVESTIGATION', 'APPEAL', 'SENTENCING', 'REVIEW')),
    ADD CONSTRAINT chk_hearings_status
        CHECK (status IN ('SCHEDULED', 'HELD', 'POSTPONED', 'CANCELLED', 'ADJOURNED')),
    ADD CONSTRAINT chk_hearings_duration
        CHECK (duration_minutes IS NULL OR duration_minutes > 0);

CREATE INDEX idx_hearings_company_date ON hearings (company_id, hearing_date);
CREATE INDEX idx_hearings_company_status_date ON hearings (company_id, status, hearing_date);
CREATE INDEX idx_hearings_case_date ON hearings (case_id, hearing_date);
CREATE INDEX idx_hearings_previous ON hearings (previous_hearing_id);