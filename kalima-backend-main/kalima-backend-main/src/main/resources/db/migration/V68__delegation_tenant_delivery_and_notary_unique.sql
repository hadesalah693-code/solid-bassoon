ALTER TABLE delegation_reminder_deliveries
    ADD COLUMN company_id UUID;

UPDATE delegation_reminder_deliveries delivery
SET company_id = delegation.company_id
FROM delegations delegation
WHERE delegation.id = delivery.delegation_id;

ALTER TABLE delegation_reminder_deliveries
    ALTER COLUMN company_id SET NOT NULL,
    ADD CONSTRAINT fk_delegation_reminder_company
        FOREIGN KEY (company_id) REFERENCES companies (id),
    DROP CONSTRAINT pk_delegation_reminder_deliveries,
    ADD CONSTRAINT pk_delegation_reminder_deliveries
        PRIMARY KEY (company_id, delegation_id, offset_days, recipient_id),
    ADD CONSTRAINT chk_delegation_reminder_offset_positive
        CHECK (offset_days > 0);

CREATE INDEX idx_delegation_reminders_company
    ON delegation_reminder_deliveries (company_id, delegation_id);

CREATE UNIQUE INDEX uq_delegations_company_notary_number
    ON delegations (company_id, lower(trim(notary_number)))
    WHERE notary_number IS NOT NULL AND length(trim(notary_number)) > 0;