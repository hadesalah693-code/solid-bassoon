-- V16: allow chatbot/system messages alongside human-authored messages.
--
-- USER messages keep the existing semantics: sender_user_id NOT NULL with the
-- FK + the historical chk_messages_sender_receiver_diff CHECK still apply
-- because the CHECK is satisfied (treated as TRUE) when either side is NULL.
--
-- BOT messages are authored by the case chatbot, not a Person, so:
--   sender_type     = 'BOT'
--   sender_user_id  IS NULL
--   receiver_user_id IS NOT NULL  (always the actor who asked the question)

ALTER TABLE messages
    ADD COLUMN sender_type VARCHAR(10) NOT NULL DEFAULT 'USER';

ALTER TABLE messages
    ADD CONSTRAINT chk_messages_sender_type
    CHECK (sender_type IN ('USER', 'BOT'));

ALTER TABLE messages
    ALTER COLUMN sender_user_id DROP NOT NULL;

-- Tie the column nullability to the sender_type so a USER message can never
-- accidentally be persisted without a Person sender, and a BOT message can
-- never accidentally claim a Person sender.
ALTER TABLE messages
    ADD CONSTRAINT chk_messages_sender_user_match_type
    CHECK (
        (sender_type = 'USER' AND sender_user_id IS NOT NULL)
        OR (sender_type = 'BOT'  AND sender_user_id IS NULL)
    );

CREATE INDEX idx_messages_case_sender_type
    ON messages (case_id, sender_type);
