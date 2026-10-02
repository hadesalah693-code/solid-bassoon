-- Admin tasks: optional case link, optional dispute link, tenant-wide indexes.

ALTER TABLE tasks ALTER COLUMN case_id DROP NOT NULL;

ALTER TABLE tasks
    ADD COLUMN dispute_id UUID,
    ADD CONSTRAINT fk_tasks_dispute
        FOREIGN KEY (dispute_id) REFERENCES disputes (id) ON DELETE SET NULL;

CREATE INDEX idx_tasks_dispute_id ON tasks (dispute_id);
CREATE INDEX idx_tasks_company_status ON tasks (company_id, status);
CREATE INDEX idx_tasks_company_assigned_status ON tasks (company_id, assigned_to, status);
CREATE INDEX idx_tasks_company_dispute ON tasks (company_id, dispute_id);
