-- Fix: mandate_events was missing the audit column updated_at that BaseAuditEntity requires.
ALTER TABLE mandate_events
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT NOW();
