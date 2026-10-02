-- Cases module: scope case_number uniqueness per tenant, and introduce an
-- archive timestamp for soft delete.
--
-- V1 created cases.case_number with a GLOBAL UNIQUE constraint (auto-named
-- cases_case_number_key by Postgres). That contradicts multi-tenancy: two
-- different law firms must be free to reuse the same number (e.g. "2026/001").

ALTER TABLE cases DROP CONSTRAINT cases_case_number_key;

ALTER TABLE cases
    ADD CONSTRAINT uq_cases_company_case_number
    UNIQUE (company_id, case_number);

-- Soft-delete / archive support. CaseStatus.ARCHIVED already exists in the
-- enum; we add the timestamp to record exactly when the archival happened and
-- let list queries transparently hide archived rows unless asked otherwise.
ALTER TABLE cases ADD COLUMN archived_at TIMESTAMP NULL;

CREATE INDEX idx_cases_archived_at     ON cases(archived_at);
CREATE INDEX idx_cases_company_status  ON cases(company_id, status);
CREATE INDEX idx_cases_assigned_lawyer ON cases(assigned_lawyer_id);
