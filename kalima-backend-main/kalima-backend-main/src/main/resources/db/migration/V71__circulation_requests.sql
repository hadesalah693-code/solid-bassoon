CREATE TABLE circulation_requests (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies (id),
    case_id UUID NOT NULL REFERENCES cases (id),
    from_person_id UUID NOT NULL REFERENCES persons (id),
    to_person_id UUID NOT NULL REFERENCES persons (id),
    requested_by UUID NOT NULL REFERENCES persons (id),
    approved_by UUID REFERENCES persons (id),
    requested_at TIMESTAMP NOT NULL DEFAULT NOW(),
    due_back_at TIMESTAMP,
    handed_at TIMESTAMP,
    returned_at TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reason VARCHAR(500) NOT NULL,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_circulation_status CHECK (
        status IN ('PENDING', 'APPROVED', 'IN_HAND', 'RETURNED', 'REJECTED', 'CANCELLED')
    ),
    CONSTRAINT chk_circulation_distinct_people CHECK (from_person_id <> to_person_id),
    CONSTRAINT chk_circulation_due_date CHECK (due_back_at IS NULL OR due_back_at > requested_at)
);

CREATE INDEX idx_circulation_company_status_date
    ON circulation_requests (company_id, status, requested_at DESC);
CREATE INDEX idx_circulation_company_inbox
    ON circulation_requests (company_id, to_person_id, status, requested_at DESC);
CREATE INDEX idx_circulation_company_outbox
    ON circulation_requests (company_id, requested_by, requested_at DESC);
CREATE INDEX idx_circulation_case ON circulation_requests (company_id, case_id, requested_at DESC);

CREATE TABLE circulation_request_events (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies (id),
    circulation_request_id UUID NOT NULL REFERENCES circulation_requests (id) ON DELETE CASCADE,
    event_type VARCHAR(20) NOT NULL,
    actor_id UUID NOT NULL REFERENCES persons (id),
    occurred_at TIMESTAMP NOT NULL DEFAULT NOW(),
    notes VARCHAR(1000),
    CONSTRAINT chk_circulation_event_type CHECK (
        event_type IN ('REQUESTED', 'APPROVED', 'REJECTED', 'HANDED_OVER', 'RETURNED', 'CANCELLED')
    )
);

CREATE INDEX idx_circulation_events_company_request
    ON circulation_request_events (company_id, circulation_request_id, occurred_at);