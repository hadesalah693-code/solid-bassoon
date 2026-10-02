-- Backfill company_id for all existing rows to the default company seeded in V5.
-- After this migration, no row in tenant-scoped tables has a NULL company_id,
-- which makes V8 safe to run (NOT NULL + FK).

UPDATE persons       SET company_id = '00000000-0000-0000-0000-000000000001' WHERE company_id IS NULL;
UPDATE cases         SET company_id = '00000000-0000-0000-0000-000000000001' WHERE company_id IS NULL;
UPDATE hearings      SET company_id = '00000000-0000-0000-0000-000000000001' WHERE company_id IS NULL;
UPDATE tasks         SET company_id = '00000000-0000-0000-0000-000000000001' WHERE company_id IS NULL;
UPDATE notifications SET company_id = '00000000-0000-0000-0000-000000000001' WHERE company_id IS NULL;
UPDATE messages      SET company_id = '00000000-0000-0000-0000-000000000001' WHERE company_id IS NULL;
