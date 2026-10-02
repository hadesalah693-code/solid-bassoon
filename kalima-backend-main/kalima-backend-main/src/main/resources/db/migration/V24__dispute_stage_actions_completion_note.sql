-- Completion audit for dispute stage actions (lawyer execution note).

ALTER TABLE dispute_stage_actions
    ADD COLUMN completion_note TEXT,
    ADD COLUMN completed_by UUID REFERENCES persons (id);

CREATE INDEX idx_dsa_completed_by ON dispute_stage_actions (completed_by);
