-- V30: Reply-to threading + emoji reactions for messages
-- Safe to re-run: uses IF NOT EXISTS / IF EXISTS guards.

-- ─────────────────────────────────────────────────────────
-- 1. Reply-to threading
-- ─────────────────────────────────────────────────────────
ALTER TABLE messages ADD COLUMN IF NOT EXISTS reply_to_message_id UUID;

-- Add FK only if not yet present (idempotent via DO block).
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_messages_reply_to'
    ) THEN
        ALTER TABLE messages
            ADD CONSTRAINT fk_messages_reply_to
            FOREIGN KEY (reply_to_message_id) REFERENCES messages(id)
            ON DELETE SET NULL;
    END IF;
END;
$$;

CREATE INDEX IF NOT EXISTS idx_messages_reply_to ON messages(reply_to_message_id)
    WHERE reply_to_message_id IS NOT NULL;

-- ─────────────────────────────────────────────────────────
-- 2. Emoji reactions
-- ─────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS message_reactions (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    company_id  UUID         NOT NULL,
    message_id  UUID         NOT NULL,
    person_id   UUID         NOT NULL,
    emoji       VARCHAR(10)  NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_message_reactions PRIMARY KEY (id),
    CONSTRAINT fk_mr_company  FOREIGN KEY (company_id) REFERENCES companies(id),
    CONSTRAINT fk_mr_message  FOREIGN KEY (message_id) REFERENCES messages(id) ON DELETE CASCADE,
    CONSTRAINT fk_mr_person   FOREIGN KEY (person_id)  REFERENCES persons(id),
    CONSTRAINT uq_mr_per_user UNIQUE (message_id, person_id, emoji)
);

CREATE INDEX IF NOT EXISTS idx_mr_message_id  ON message_reactions(message_id);
CREATE INDEX IF NOT EXISTS idx_mr_company_msg ON message_reactions(company_id, message_id);
