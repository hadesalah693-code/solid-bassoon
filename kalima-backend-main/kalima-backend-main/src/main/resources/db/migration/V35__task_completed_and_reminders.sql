-- Task completion timestamp and due-date reminder deduplication.

ALTER TABLE tasks ADD COLUMN completed_at TIMESTAMP;
ALTER TABLE tasks ADD COLUMN due_soon_notified_at TIMESTAMP;
ALTER TABLE tasks ADD COLUMN overdue_notified_at TIMESTAMP;

CREATE INDEX idx_tasks_company_due_open
    ON tasks (company_id, due_date)
    WHERE status IN ('TODO', 'IN_PROGRESS');

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
    'TASK_OVERDUE'
));
