-- Delegations M2: case links, attachments, reminder delivery tracking.

CREATE TABLE delegation_case_links (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies (id),
    delegation_id UUID NOT NULL REFERENCES delegations (id) ON DELETE CASCADE,
    case_id UUID NOT NULL REFERENCES cases (id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_delegation_case_links UNIQUE (delegation_id, case_id)
);

CREATE INDEX idx_delegation_case_links_delegation ON delegation_case_links (delegation_id);
CREATE INDEX idx_delegation_case_links_case ON delegation_case_links (case_id);

CREATE TABLE delegation_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies (id),
    delegation_id UUID NOT NULL REFERENCES delegations (id) ON DELETE CASCADE,
    uploaded_by UUID NOT NULL REFERENCES persons (id),
    original_filename VARCHAR(255) NOT NULL,
    stored_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(150) NOT NULL,
    size_bytes BIGINT NOT NULL,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_delegation_documents_delegation ON delegation_documents (delegation_id);

CREATE TABLE delegation_reminder_deliveries (
    delegation_id UUID NOT NULL REFERENCES delegations (id) ON DELETE CASCADE,
    offset_days INTEGER NOT NULL,
    recipient_id UUID NOT NULL REFERENCES persons (id),
    notified_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_delegation_reminder_deliveries PRIMARY KEY (delegation_id, offset_days, recipient_id)
);

CREATE INDEX idx_delegation_reminder_deliveries_recipient ON delegation_reminder_deliveries (recipient_id);
