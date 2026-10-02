-- Phase 1: dispute timeline foundation (tenant-scoped, per case).

CREATE TABLE dispute_events (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    case_id UUID NOT NULL,
    type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    title VARCHAR(250) NOT NULL,
    summary TEXT,
    event_date TIMESTAMP,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_dispute_events_company FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_dispute_events_case FOREIGN KEY (case_id) REFERENCES cases (id)
);

CREATE INDEX idx_dispute_events_company_id ON dispute_events (company_id);
CREATE INDEX idx_dispute_events_case_id ON dispute_events (case_id);
CREATE INDEX idx_dispute_events_status ON dispute_events (status);
CREATE INDEX idx_dispute_events_event_date ON dispute_events (event_date);
