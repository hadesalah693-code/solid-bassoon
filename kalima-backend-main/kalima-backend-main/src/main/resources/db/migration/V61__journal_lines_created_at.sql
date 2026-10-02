-- Journal lines were the only V60 table created without audit timestamps, while
-- the JournalLine entity maps created_at. Added separately because V60 is
-- already applied in existing environments (Flyway checksums are immutable).

ALTER TABLE journal_lines
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT NOW();
