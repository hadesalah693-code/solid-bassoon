-- ---------------------------------------------------------------------------
-- V63 — Accounting core integrity
--
-- 1. accounting_sequences.scope gains the scopes required by the law-firm
--    financial features (vendor bills, payments, retainers, credit/debit notes,
--    refunds). The V60 CHECK only allowed JOURNAL/EXPENSE/RECEIPT.
-- 2. invoices.invoice_number becomes unique per company instead of globally.
--    The V59 constraint was UNIQUE (invoice_number) while the sequence is
--    per-company, so the second company to invoice always collided with the
--    first company's "INV-2026-00001".
-- 3. journal_lines may no longer contain a zero-value line (debit = 0 AND
--    credit = 0). The V60 CHECK explicitly allowed that combination.
-- 4. Overlapping financial periods are rejected by the database, not only by
--    the service layer.
-- 5. A journal line must belong to the same company as its entry.
--
-- Every step below is guarded by a preflight check that fails with an
-- actionable message. A ledger migration must never silently delete or rewrite
-- financial rows to make a constraint fit, so if existing data violates one of
-- these rules the migration aborts and names the offending rows instead.
-- ---------------------------------------------------------------------------

-- 1. Sequence scopes -------------------------------------------------------
ALTER TABLE accounting_sequences DROP CONSTRAINT IF EXISTS chk_accounting_sequences_scope;
ALTER TABLE accounting_sequences ADD CONSTRAINT chk_accounting_sequences_scope CHECK (
    scope IN ('JOURNAL', 'EXPENSE', 'RECEIPT',
              'VENDOR_BILL', 'PAYMENT', 'RETAINER',
              'CREDIT_NOTE', 'DEBIT_NOTE', 'REFUND')
);

-- 2. Invoice numbers are tenant-scoped ------------------------------------
DO $$
DECLARE dupes text;
BEGIN
    SELECT string_agg(company_id::text || '/' || invoice_number, ', ' ORDER BY company_id, invoice_number)
      INTO dupes
      FROM (SELECT company_id, invoice_number
              FROM invoices
             GROUP BY company_id, invoice_number
            HAVING count(*) > 1) d;
    IF dupes IS NOT NULL THEN
        RAISE EXCEPTION
            'V63: cannot make invoice_number unique per company, duplicate rows exist for: %', dupes;
    END IF;
END $$;

ALTER TABLE invoices DROP CONSTRAINT IF EXISTS uq_invoices_invoice_number;
ALTER TABLE invoices ADD CONSTRAINT uq_invoices_company_invoice_number UNIQUE (company_id, invoice_number);

-- 3. A journal line must carry a real amount on exactly one side -----------
DO $$
DECLARE zero_count bigint;
BEGIN
    SELECT count(*) INTO zero_count
      FROM journal_lines
     WHERE (debit = 0 AND credit = 0) OR debit < 0 OR credit < 0;
    IF zero_count > 0 THEN
        RAISE EXCEPTION
            'V63: % journal_lines row(s) carry a zero or negative amount. '
            'Review them and post a correcting entry before retrying this migration; '
            'no ledger row was modified.', zero_count;
    END IF;
END $$;

ALTER TABLE journal_lines DROP CONSTRAINT IF EXISTS chk_journal_lines_amount;
ALTER TABLE journal_lines ADD CONSTRAINT chk_journal_lines_amount CHECK (
    (debit > 0 AND credit = 0) OR (credit > 0 AND debit = 0)
);

-- 4. Overlapping financial periods must be impossible ----------------------
-- A trigger is used instead of a GiST exclusion constraint on purpose:
-- `CREATE EXTENSION btree_gist` needs superuser rights that managed PostgreSQL
-- (RDS, Cloud SQL, most container images) do not grant to the migration user.
-- The bound semantics match FinancialPeriodService#overlaps exactly — both
-- ranges are inclusive, so periods that share a boundary date do overlap.
DO $$
DECLARE overlaps_found text;
BEGIN
    SELECT string_agg(a.name || ' <-> ' || b.name, ', ' ORDER BY a.name, b.name)
      INTO overlaps_found
      FROM financial_periods a
      JOIN financial_periods b
        ON a.company_id = b.company_id
       AND a.id <> b.id
       AND a.start_date <= b.end_date
       AND b.start_date <= a.end_date;
    IF overlaps_found IS NOT NULL THEN
        RAISE EXCEPTION
            'V63: overlapping financial periods already exist (%). '
            'Close or delete them before retrying; no period was modified.', overlaps_found;
    END IF;
END $$;

ALTER TABLE financial_periods DROP CONSTRAINT IF EXISTS uq_financial_periods_company_dates;

CREATE OR REPLACE FUNCTION trg_financial_periods_no_overlap()
RETURNS TRIGGER AS $$
DECLARE conflict_name text;
BEGIN
    SELECT p.name INTO conflict_name
      FROM financial_periods p
     WHERE p.company_id = NEW.company_id
       AND p.id <> NEW.id
       AND p.start_date <= NEW.end_date
       AND NEW.start_date <= p.end_date
     LIMIT 1;
    IF conflict_name IS NOT NULL THEN
        RAISE EXCEPTION
            'overlapping financial period: "%" overlaps "%"', NEW.name, conflict_name
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_financial_periods_no_overlap ON financial_periods;
CREATE TRIGGER trg_financial_periods_no_overlap
    BEFORE INSERT OR UPDATE ON financial_periods
    FOR EACH ROW EXECUTE FUNCTION trg_financial_periods_no_overlap();

-- 5. A journal line must belong to the same company as its entry ----------
-- journal_entries has a single-column primary key, so a composite foreign key to
-- (company_id, id) needs an explicit unique constraint on those two columns
-- first; without it PostgreSQL rejects the FK with "no unique constraint
-- matching given keys". id alone is already unique as the PK, so this adds no
-- real redundancy and locks nothing back that cannot be re-added.
ALTER TABLE journal_entries
    ADD CONSTRAINT uq_journal_entries_company_id UNIQUE (company_id, id);

ALTER TABLE journal_lines DROP CONSTRAINT IF EXISTS fk_journal_lines_company_entry;

DO $$
DECLARE mismatched bigint;
BEGIN
    SELECT count(*) INTO mismatched
      FROM journal_lines l
      JOIN journal_entries e ON e.id = l.entry_id
     WHERE e.company_id IS DISTINCT FROM l.company_id;
    IF mismatched > 0 THEN
        RAISE EXCEPTION
            'V63: % journal_lines row(s) have a company_id that differs from their entry. '
            'No row was modified.', mismatched;
    END IF;
END $$;

ALTER TABLE journal_lines ADD CONSTRAINT fk_journal_lines_company_entry
    FOREIGN KEY (company_id, entry_id)
    REFERENCES journal_entries (company_id, id)
    ON DELETE CASCADE;
