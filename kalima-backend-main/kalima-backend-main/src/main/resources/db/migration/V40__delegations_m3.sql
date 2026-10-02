-- Delegations M3: starred flag for favourites / quick filters.

ALTER TABLE delegations ADD COLUMN starred BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_delegations_starred ON delegations (company_id, starred) WHERE starred = TRUE;
