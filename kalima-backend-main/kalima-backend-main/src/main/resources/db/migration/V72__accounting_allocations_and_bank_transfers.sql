ALTER TABLE journal_entries
    DROP CONSTRAINT IF EXISTS chk_journal_entries_source;
ALTER TABLE journal_entries
    ADD CONSTRAINT chk_journal_entries_source CHECK (
        source IN ('MANUAL', 'INVOICE', 'PAYMENT', 'EXPENSE', 'RECEIPT',
                   'OPENING', 'REVERSAL', 'BANK_TRANSFER')
    );

ALTER TABLE receipts
    ADD COLUMN invoice_id UUID REFERENCES invoices (id),
    ADD COLUMN voided_at TIMESTAMP;
CREATE INDEX idx_receipts_company_invoice
    ON receipts (company_id, invoice_id)
    WHERE invoice_id IS NOT NULL;

ALTER TABLE expenses ADD COLUMN voided_at TIMESTAMP;

ALTER TABLE invoice_payments
    ADD COLUMN receipt_id UUID REFERENCES receipts (id),
    ADD COLUMN voided_at TIMESTAMP;
CREATE INDEX idx_invoice_payments_receipt
    ON invoice_payments (company_id, receipt_id)
    WHERE receipt_id IS NOT NULL;

CREATE TABLE bank_transfers (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies (id),
    source_bank_account_id UUID NOT NULL REFERENCES bank_accounts (id),
    destination_bank_account_id UUID NOT NULL REFERENCES bank_accounts (id),
    amount NUMERIC(19,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    transfer_date DATE NOT NULL,
    reference VARCHAR(100),
    notes TEXT,
    journal_entry_id UUID REFERENCES journal_entries (id),
    created_by UUID NOT NULL REFERENCES persons (id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_bank_transfers_amount CHECK (amount > 0),
    CONSTRAINT chk_bank_transfers_different_accounts
        CHECK (source_bank_account_id <> destination_bank_account_id),
    CONSTRAINT uq_bank_transfers_journal UNIQUE (journal_entry_id)
);

CREATE INDEX idx_bank_transfers_company_date
    ON bank_transfers (company_id, transfer_date DESC);