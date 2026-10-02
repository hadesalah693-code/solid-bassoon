-- Soft-archive support for persons (clients and non-staff deletions from UI).
-- Archived rows are hidden from default listings; login is blocked for archived logins.
ALTER TABLE persons ADD COLUMN archived_at TIMESTAMP NULL;

CREATE INDEX idx_persons_archived_at ON persons(archived_at) WHERE archived_at IS NOT NULL;
