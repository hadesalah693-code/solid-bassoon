-- Calendar hub: projected + native events, reminders, notification types.

CREATE TABLE calendar_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id      UUID         NOT NULL REFERENCES companies(id),
    title           VARCHAR(200) NOT NULL,
    description     TEXT,
    event_type      VARCHAR(30)  NOT NULL CHECK (event_type IN (
        'HEARING', 'TASK', 'MEETING', 'CALL', 'REMINDER', 'DEADLINE',
        'CONTRACT_EXPIRY', 'INVOICE_DUE', 'DOCUMENT_EXPIRY', 'CUSTOM'
    )),
    source_module   VARCHAR(30) CHECK (source_module IS NULL OR source_module IN (
        'HEARING', 'TASK', 'DISPUTE_ACTION', 'DISPUTE_EVENT',
        'CONTRACT', 'INVOICE', 'DOCUMENT'
    )),
    source_id       UUID,
    case_id         UUID REFERENCES cases(id) ON DELETE SET NULL,
    task_id         UUID REFERENCES tasks(id) ON DELETE SET NULL,
    client_id       UUID REFERENCES persons(id) ON DELETE SET NULL,
    dispute_id      UUID REFERENCES disputes(id) ON DELETE SET NULL,
    start_at        TIMESTAMP    NOT NULL,
    end_at          TIMESTAMP,
    all_day         BOOLEAN      NOT NULL DEFAULT FALSE,
    status          VARCHAR(20)  NOT NULL DEFAULT 'SCHEDULED' CHECK (status IN (
        'SCHEDULED', 'COMPLETED', 'CANCELLED'
    )),
    priority        VARCHAR(20) CHECK (priority IS NULL OR priority IN (
        'LOW', 'MEDIUM', 'HIGH', 'URGENT'
    )),
    assigned_to     UUID REFERENCES persons(id) ON DELETE SET NULL,
    created_by      UUID         NOT NULL REFERENCES persons(id),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_calendar_projection UNIQUE (company_id, source_module, source_id)
);

CREATE INDEX idx_calendar_events_company_start ON calendar_events(company_id, start_at);
CREATE INDEX idx_calendar_events_company_type_start ON calendar_events(company_id, event_type, start_at);
CREATE INDEX idx_calendar_events_case_id ON calendar_events(case_id);
CREATE INDEX idx_calendar_events_client_id ON calendar_events(client_id);
CREATE INDEX idx_calendar_events_assigned_to ON calendar_events(assigned_to, start_at);
CREATE INDEX idx_calendar_events_status_start ON calendar_events(company_id, status, start_at);

CREATE TABLE calendar_event_reminders (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id          UUID      NOT NULL REFERENCES companies(id),
    calendar_event_id   UUID      NOT NULL REFERENCES calendar_events(id) ON DELETE CASCADE,
    offset_minutes      INT       NOT NULL CHECK (offset_minutes > 0),
    notified_at         TIMESTAMP,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_calendar_event_reminder UNIQUE (calendar_event_id, offset_minutes)
);

CREATE INDEX idx_calendar_reminders_pending ON calendar_event_reminders(company_id, notified_at);

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
    'CALENDAR_REMINDER'
));
