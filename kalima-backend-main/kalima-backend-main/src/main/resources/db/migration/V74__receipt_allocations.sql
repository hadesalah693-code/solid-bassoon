-- V74: first-class receipt -> invoice allocation ledger.
--
-- Allocation used to live in a single nullable column, receipts.invoice_id, which
-- capped a receipt at one invoice and made un-applying a single invoice impossible:
-- the only way to undo it was ReceiptService.delete(), which reverses the entire
-- receipt. Refunds, credit/debit notes and the payables side all need to settle
-- against a reusable allocation primitive, so it is modelled properly here:
--
--   * one receipt may be spread over many invoices (partial allocation allowed),
--   * each allocation row can be voided on its own, with its own reversal entry,
--   * receipts.invoice_id stays as a single-invoice shortcut for existing callers.
--
-- Two invariants cannot be expressed as CHECK constraints because they span rows,
-- so they are enforced by the trigger below:
--   1. live allocations for a receipt never exceed the receipt amount,
--   2. live allocations for an invoice never push invoice.amount_paid past
--      invoice.total (the same ceiling InvoiceService.recordPayment() enforces).

CREATE TABLE receipt_allocations (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies (id),
    receipt_id UUID NOT NULL REFERENCES receipts (id),
    invoice_id UUID NOT NULL REFERENCES invoices (id),
    amount NUMERIC(19,2) NOT NULL,
    allocation_date DATE NOT NULL,
    voided_at TIMESTAMP,
    reversal_journal_entry_id UUID REFERENCES journal_entries (id),
    allocated_by UUID NOT NULL REFERENCES persons (id),
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_receipt_allocations_amount CHECK (amount > 0)
);

CREATE INDEX idx_receipt_allocations_receipt
    ON receipt_allocations (company_id, receipt_id);
CREATE INDEX idx_receipt_allocations_invoice
    ON receipt_allocations (company_id, invoice_id);
CREATE INDEX idx_receipt_allocations_live
    ON receipt_allocations (company_id) WHERE voided_at IS NULL;

-- Backfill from the legacy single-invoice column. Voided receipts are skipped:
-- their journal was already reversed and invoice.amount_paid already restored.
INSERT INTO receipt_allocations (
    id, company_id, receipt_id, invoice_id, amount, allocation_date,
    allocated_by, created_at)
SELECT gen_random_uuid(),
       r.company_id,
       r.id,
       r.invoice_id,
       r.amount,
       r.receipt_date,
       r.recorded_by,
       r.created_at
FROM receipts r
WHERE r.invoice_id IS NOT NULL
  AND r.voided_at IS NULL;

-- 1 + 2: over-allocation guards ---------------------------------------------
CREATE OR REPLACE FUNCTION trg_receipt_allocations_invariants()
RETURNS TRIGGER AS $$
DECLARE
    receipt_amount NUMERIC(19,2);
    receipt_total  NUMERIC(19,2);
    invoice_total  NUMERIC(19,2);
    invoice_paid   NUMERIC(19,2);
    new_amount     NUMERIC(19,2) := COALESCE(NEW.amount, 0);
BEGIN
    IF NEW.voided_at IS NOT NULL THEN
        RETURN NEW;
    END IF;

    SELECT amount INTO receipt_amount FROM receipts WHERE id = NEW.receipt_id;
    SELECT COALESCE(SUM(a.amount), 0)
      INTO receipt_total
      FROM receipt_allocations a
     WHERE a.receipt_id = NEW.receipt_id
       AND a.voided_at IS NULL
       AND (TG_OP = 'INSERT' OR a.id <> NEW.id);

    IF receipt_total > receipt_amount THEN
        RAISE EXCEPTION
            'receipt allocation exceeds the receipt amount (allocated %, receipt %)',
            receipt_total, receipt_amount
            USING ERRCODE = 'check_violation';
    END IF;

    SELECT total, amount_paid INTO invoice_total, invoice_paid
      FROM invoices WHERE id = NEW.invoice_id;

    IF invoice_paid + new_amount > invoice_total THEN
        RAISE EXCEPTION
            'receipt allocation exceeds the invoice remaining balance (invoice %, remaining %)',
            NEW.invoice_id, invoice_total - invoice_paid
            USING ERRCODE = 'check_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS receipt_allocations_invariants ON receipt_allocations;
CREATE TRIGGER receipt_allocations_invariants
    BEFORE INSERT OR UPDATE OF amount, invoice_id, receipt_id, voided_at
    ON receipt_allocations
    FOR EACH ROW EXECUTE FUNCTION trg_receipt_allocations_invariants();