-- Indexes for notification recipient lookups.
-- NotificationRepository.findByRecipientId() filters only by recipient_user_id;
-- without an index this is a full table scan as notifications accumulate.

CREATE INDEX IF NOT EXISTS idx_notifications_recipient_id
    ON notifications (recipient_user_id);

-- Partial index for the common "unread" filter — smaller, faster than the full index.
CREATE INDEX IF NOT EXISTS idx_notifications_recipient_unread
    ON notifications (recipient_user_id)
    WHERE is_read = FALSE;
