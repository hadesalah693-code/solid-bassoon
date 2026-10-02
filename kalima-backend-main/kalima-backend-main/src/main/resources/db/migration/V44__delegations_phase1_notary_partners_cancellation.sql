-- Delegations Phase 1: official notarization number, partners, optional expiry,
-- cancellation dates, and document type classification. Additive only.

-- Pre-existing gap: the calendar module's source_module check constraint (V36) was
-- never widened for 'DELEGATION' when the delegations module (V38) started projecting
-- expiry events into calendar_events. Fixed here since Phase 1 exercises this path.
ALTER TABLE calendar_events DROP CONSTRAINT IF EXISTS calendar_events_source_module_check;
ALTER TABLE calendar_events ADD CONSTRAINT calendar_events_source_module_check
    CHECK (source_module IS NULL OR source_module IN (
        'HEARING', 'TASK', 'DISPUTE_ACTION', 'DISPUTE_EVENT',
        'CONTRACT', 'INVOICE', 'DOCUMENT', 'DELEGATION'
    ));

ALTER TABLE delegations ALTER COLUMN expires_at DROP NOT NULL;

ALTER TABLE delegations ADD COLUMN notary_number VARCHAR(50);
ALTER TABLE delegations ADD COLUMN cancelled_at DATE;
ALTER TABLE delegations ADD COLUMN cancellation_announcement_at DATE;

CREATE INDEX idx_delegations_notary_number ON delegations (company_id, notary_number);

CREATE TABLE delegation_partners (
    delegation_id UUID NOT NULL REFERENCES delegations (id) ON DELETE CASCADE,
    person_id UUID NOT NULL REFERENCES persons (id),
    CONSTRAINT pk_delegation_partners PRIMARY KEY (delegation_id, person_id)
);

CREATE INDEX idx_delegation_partners_person ON delegation_partners (person_id);

ALTER TABLE delegation_documents
    ADD COLUMN document_type VARCHAR(20) NOT NULL DEFAULT 'OTHER';

CREATE INDEX idx_delegation_documents_type ON delegation_documents (delegation_id, document_type);
