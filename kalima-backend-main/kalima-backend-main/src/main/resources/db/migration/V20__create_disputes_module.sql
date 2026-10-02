-- Pre-case disputes module: tenant-scoped lifecycle tracking + timeline + optional case conversion.

CREATE TABLE disputes (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    client_id UUID,
    opponent_name VARCHAR(250) NOT NULL,
    title VARCHAR(250) NOT NULL,
    description TEXT,
    dispute_type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    legal_path VARCHAR(50),
    current_step VARCHAR(30) NOT NULL DEFAULT 'CREATED',
    claim_amount NUMERIC(19, 2),
    risk_level VARCHAR(20) NOT NULL,
    success_probability INTEGER,
    assigned_lawyer_id UUID,
    converted_case_id UUID,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_disputes_company FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_disputes_client FOREIGN KEY (client_id) REFERENCES persons (id),
    CONSTRAINT fk_disputes_assigned_lawyer FOREIGN KEY (assigned_lawyer_id) REFERENCES persons (id),
    CONSTRAINT fk_disputes_converted_case FOREIGN KEY (converted_case_id) REFERENCES cases (id)
);

CREATE INDEX idx_disputes_company_id ON disputes (company_id);
CREATE INDEX idx_disputes_status ON disputes (status);
CREATE INDEX idx_disputes_dispute_type ON disputes (dispute_type);
CREATE INDEX idx_disputes_assigned_lawyer_id ON disputes (assigned_lawyer_id);
CREATE INDEX idx_disputes_converted_case_id ON disputes (converted_case_id);
CREATE INDEX idx_disputes_current_step ON disputes (current_step);

CREATE TABLE dispute_timeline_events (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    dispute_id UUID NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    title VARCHAR(500) NOT NULL,
    description TEXT,
    created_by UUID,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_dte_company FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_dte_dispute FOREIGN KEY (dispute_id) REFERENCES disputes (id),
    CONSTRAINT fk_dte_created_by FOREIGN KEY (created_by) REFERENCES persons (id)
);

CREATE INDEX idx_dte_dispute_id ON dispute_timeline_events (dispute_id);
CREATE INDEX idx_dte_company_id ON dispute_timeline_events (company_id);
