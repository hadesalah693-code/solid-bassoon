-- Powers of attorney (التوكيلات) — M1 core table + reminder config + official numbers.

CREATE TABLE delegation_number_counters (
    company_id UUID NOT NULL,
    year INTEGER NOT NULL,
    next_number INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_delegation_number_counters PRIMARY KEY (company_id, year),
    CONSTRAINT fk_dlgnc_company FOREIGN KEY (company_id) REFERENCES companies (id)
);

CREATE INDEX idx_dlgnc_company ON delegation_number_counters (company_id);

CREATE TABLE delegations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies (id),
    delegation_number VARCHAR(32) NOT NULL,
    title VARCHAR(250) NOT NULL,
    client_id UUID NOT NULL REFERENCES persons (id),
    agent_id UUID REFERENCES persons (id),
    delegation_type VARCHAR(30) NOT NULL,
    issuing_country VARCHAR(2) NOT NULL DEFAULT 'QA',
    status VARCHAR(20) NOT NULL,
    cancel_reason VARCHAR(30),
    cancel_reason_note VARCHAR(500),
    issued_at DATE NOT NULL,
    expires_at DATE NOT NULL,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_delegations_company_number UNIQUE (company_id, delegation_number)
);

CREATE INDEX idx_delegations_company ON delegations (company_id);
CREATE INDEX idx_delegations_client ON delegations (client_id);
CREATE INDEX idx_delegations_agent ON delegations (agent_id);
CREATE INDEX idx_delegations_status ON delegations (company_id, status);
CREATE INDEX idx_delegations_expires ON delegations (company_id, expires_at);

CREATE TABLE delegation_reminder_offsets (
    delegation_id UUID NOT NULL REFERENCES delegations (id) ON DELETE CASCADE,
    offset_days INTEGER NOT NULL,
    CONSTRAINT pk_delegation_reminder_offsets PRIMARY KEY (delegation_id, offset_days),
    CONSTRAINT chk_delegation_reminder_offset_positive CHECK (offset_days > 0)
);

CREATE TABLE delegation_reminder_recipients (
    delegation_id UUID NOT NULL REFERENCES delegations (id) ON DELETE CASCADE,
    person_id UUID NOT NULL REFERENCES persons (id),
    CONSTRAINT pk_delegation_reminder_recipients PRIMARY KEY (delegation_id, person_id)
);

CREATE INDEX idx_delegation_reminder_recipients_person ON delegation_reminder_recipients (person_id);
