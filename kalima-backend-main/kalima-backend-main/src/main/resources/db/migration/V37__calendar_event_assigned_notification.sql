-- Calendar event assignment notifications (distinct from TASK_ASSIGNED).

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
    'CALENDAR_EVENT_ASSIGNED'
));
