-- V76: fix the receipt-side invariant in trg_receipt_allocations_invariants.
--
-- V74 summed only the *existing* allocation rows and compared that against the
-- receipt amount, forgetting the row being inserted. A BEFORE INSERT trigger runs
-- before the row exists, so allocating 230 then 30 against a 250 receipt summed to
-- 230, passed the check, and over-allocated the receipt by 10.
--
-- The fix is to exclude NEW by id (which also makes UPDATE correct - it ignores the
-- old version of the row) and then add NEW.amount back in. Voiding still skips both
-- checks, and moving an allocation to a different receipt needs no extra guard:
-- the old receipt can only lose an allocation, never gain one.
--
-- CREATE OR REPLACE keeps the checksum history untouched, which is why this is a
-- new migration rather than an edit to V74.

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
       AND a.id <> NEW.id;

    -- Excluding NEW above and adding it back here is what makes the sum the
    -- post-write total: on INSERT no row matches yet, on UPDATE the pre-image is
    -- skipped. Without the addition the check passed one allocation too many.
    receipt_total := receipt_total + new_amount;

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