ALTER TABLE cases
    ADD COLUMN priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    ADD COLUMN parent_case_id UUID;

ALTER TABLE cases
    ADD CONSTRAINT chk_cases_priority
        CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
    ADD CONSTRAINT fk_cases_parent_case
        FOREIGN KEY (parent_case_id) REFERENCES cases (id);

CREATE INDEX idx_cases_company_priority ON cases (company_id, priority);
CREATE INDEX idx_cases_parent_case ON cases (parent_case_id);

CREATE TABLE case_number_counters (
    company_id UUID NOT NULL REFERENCES companies (id),
    case_year INTEGER NOT NULL,
    last_value INTEGER NOT NULL,
    PRIMARY KEY (company_id, case_year),
    CONSTRAINT chk_case_number_counter_positive CHECK (last_value > 0)
);

CREATE TABLE case_internal_notes (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies (id),
    case_id UUID NOT NULL REFERENCES cases (id) ON DELETE CASCADE,
    author_id UUID NOT NULL REFERENCES persons (id),
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_case_internal_notes_content CHECK (length(trim(content)) > 0)
);

CREATE INDEX idx_case_internal_notes_company_case
    ON case_internal_notes (company_id, case_id, created_at DESC);