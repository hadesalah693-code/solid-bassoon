-- Allow task-assignment notifications (and agent types added in Java earlier).

ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check;

ALTER TABLE notifications ADD CONSTRAINT notifications_type_check CHECK (type IN (
    'HEARING_ADDED',
    'HEARING_UPDATED',
    'CASE_STATUS_CHANGED',
    'NEW_MESSAGE',
    'AGENT_REMINDER',
    'AGENT_NIGHT_INSIGHT',
    'TASK_ASSIGNED'
));
