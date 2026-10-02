-- Party legal capacity on disputes + work-team roster (Odoo-aligned dispute module).

ALTER TABLE disputes
    ADD COLUMN client_role VARCHAR(30),
    ADD COLUMN opponent_role VARCHAR(30);

CREATE TABLE dispute_team_members (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    dispute_id UUID NOT NULL,
    role VARCHAR(40) NOT NULL,
    person_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_dtm_company FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_dtm_dispute FOREIGN KEY (dispute_id) REFERENCES disputes (id) ON DELETE CASCADE,
    CONSTRAINT fk_dtm_person FOREIGN KEY (person_id) REFERENCES persons (id),
    CONSTRAINT uq_dtm_dispute_role UNIQUE (dispute_id, role)
);

CREATE INDEX idx_dtm_dispute_id ON dispute_team_members (dispute_id);
CREATE INDEX idx_dtm_company_id ON dispute_team_members (company_id);
CREATE INDEX idx_dtm_person_id ON dispute_team_members (person_id);

ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check;

ALTER TABLE notifications ADD CONSTRAINT notifications_type_check CHECK (type IN (
    'HEARING_ADDED',
    'HEARING_UPDATED',
    'CASE_STATUS_CHANGED',
    'NEW_MESSAGE',
    'AGENT_REMINDER',
    'AGENT_NIGHT_INSIGHT',
    'TASK_ASSIGNED',
    'TASK_DUE_SOON',
    'TASK_OVERDUE',
    'CALENDAR_REMINDER',
    'CALENDAR_EVENT_ASSIGNED',
    'DELEGATION_EXPIRING',
    'DELEGATION_MENTION',
    'DELEGATION_REMINDER_ADDED',
    'DISPUTE_LAWYER_CHANGED'
));
