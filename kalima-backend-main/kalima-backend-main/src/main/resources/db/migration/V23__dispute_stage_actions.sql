-- Professional actionable tasks per dispute stage (or dispute-wide when stage_id is null).

CREATE TABLE dispute_stage_actions (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    dispute_id UUID NOT NULL,
    stage_id UUID,
    action_type VARCHAR(40) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(40) NOT NULL DEFAULT 'OPEN',
    priority VARCHAR(40) NOT NULL DEFAULT 'MEDIUM',
    due_at TIMESTAMP,
    completed_at TIMESTAMP,
    assigned_to UUID,
    created_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_dsa_company FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_dsa_dispute FOREIGN KEY (dispute_id) REFERENCES disputes (id) ON DELETE CASCADE,
    CONSTRAINT fk_dsa_stage FOREIGN KEY (stage_id) REFERENCES dispute_stages (id) ON DELETE SET NULL,
    CONSTRAINT fk_dsa_assigned FOREIGN KEY (assigned_to) REFERENCES persons (id),
    CONSTRAINT fk_dsa_created_by FOREIGN KEY (created_by) REFERENCES persons (id)
);

CREATE INDEX idx_dsa_company_id ON dispute_stage_actions (company_id);
CREATE INDEX idx_dsa_dispute_id ON dispute_stage_actions (dispute_id);
CREATE INDEX idx_dsa_stage_id ON dispute_stage_actions (stage_id);
CREATE INDEX idx_dsa_status ON dispute_stage_actions (status);
CREATE INDEX idx_dsa_priority ON dispute_stage_actions (priority);
CREATE INDEX idx_dsa_due_at ON dispute_stage_actions (due_at);
CREATE INDEX idx_dsa_assigned_to ON dispute_stage_actions (assigned_to);
CREATE INDEX idx_dsa_company_dispute ON dispute_stage_actions (company_id, dispute_id);
CREATE INDEX idx_dsa_company_stage ON dispute_stage_actions (company_id, stage_id);
CREATE INDEX idx_dsa_company_status_due ON dispute_stage_actions (company_id, status, due_at);
