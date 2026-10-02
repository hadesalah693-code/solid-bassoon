-- Add company_id to every tenant-scoped table.
-- Nullable at first so this migration can run on a populated database without
-- violating constraints. V7 backfills all existing rows to the default company,
-- and V8 then switches the columns to NOT NULL + adds foreign keys.

ALTER TABLE persons       ADD COLUMN company_id UUID;
ALTER TABLE cases         ADD COLUMN company_id UUID;
ALTER TABLE hearings      ADD COLUMN company_id UUID;
ALTER TABLE tasks         ADD COLUMN company_id UUID;
ALTER TABLE notifications ADD COLUMN company_id UUID;
ALTER TABLE messages      ADD COLUMN company_id UUID;
