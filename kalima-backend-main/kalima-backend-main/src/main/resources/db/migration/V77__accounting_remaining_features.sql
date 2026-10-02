-- V77: tables for the remaining accounting features (notes, refunds, time
-- billing, recurring invoices, payables, reconciliation, budgets) plus the ledger
-- case tag that case profitability depends on.
--
-- Two deliberate choices:
--   * Budgets have NO journal entry. A budget is a management control, not an
--     accounting fact; posting it would let the ledger be edited by planning.
--     Budgets compare a stored target against actuals read out of journal_lines.
--   * journal_lines gains case_file_id so case profitability is answered from the
--     ledger rather than by re-deriving figures from invoices and expenses, which
--     would silently disagree with the accounts.

-- 1. Ledger case tag --------------------------------------------------
ALTER TABLE journal_lines ADD COLUMN IF NOT EXISTS case_file_id UUID REFERENCES cases (id);
CREATE INDEX IF NOT EXISTS idx_journal_lines_case ON journal_lines (company_id, case_file_id);

ALTER TABLE expenses ADD COLUMN IF NOT EXISTS case_file_id UUID REFERENCES cases (id);
CREATE INDEX IF NOT EXISTS idx_expenses_case ON expenses (company_id, case_file_id);

-- Time entries need their own document number.
ALTER TABLE accounting_sequences DROP CONSTRAINT IF EXISTS chk_accounting_sequences_scope;
ALTER TABLE accounting_sequences ADD CONSTRAINT chk_accounting_sequences_scope CHECK (
    scope IN ('JOURNAL', 'EXPENSE', 'RECEIPT', 'VENDOR_BILL', 'PAYMENT', 'RETAINER',
              'CREDIT_NOTE', 'DEBIT_NOTE', 'REFUND', 'TIME_ENTRY')
);

-- 2. Credit / debit notes ----------------------------------------------
CREATE TABLE credit_debit_notes (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies (id),
    note_number VARCHAR(32) NOT NULL,
    note_type VARCHAR(20) NOT NULL,
    note_date DATE NOT NULL,
    client_id UUID NOT NULL REFERENCES persons (id),
    invoice_id UUID REFERENCES invoices (id),
    reason VARCHAR(300) NOT NULL,
    subtotal NUMERIC(19,2) NOT NULL,
    tax_rate NUMERIC(5,2) NOT NULL DEFAULT 0,
    tax_amount NUMERIC(19,2) NOT NULL DEFAULT 0,
    total NUMERIC(19,2) NOT NULL,
    journal_entry_id UUID REFERENCES journal_entries (id),
    notes TEXT,
    created_by UUID NOT NULL REFERENCES persons (id),
    voided_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_notes_type CHECK (note_type IN ('CREDIT', 'DEBIT')),
    CONSTRAINT chk_notes_subtotal CHECK (subtotal > 0),
    CONSTRAINT chk_notes_tax_rate CHECK (tax_rate >= 0 AND tax_rate <= 100),
    CONSTRAINT chk_notes_total CHECK (total > 0),
    CONSTRAINT uq_credit_debit_notes_number UNIQUE (company_id, note_number),
    CONSTRAINT uq_credit_debit_notes_journal UNIQUE (journal_entry_id)
);
CREATE INDEX idx_credit_debit_notes_client
    ON credit_debit_notes (company_id, client_id, note_date DESC);

-- 3. Refunds ----------------------------------------------------------
CREATE TABLE refunds (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies (id),
    refund_number VARCHAR(32) NOT NULL,
    refund_date DATE NOT NULL,
    client_id UUID NOT NULL REFERENCES persons (id),
    invoice_id UUID REFERENCES invoices (id),
    allocation_id UUID REFERENCES receipt_allocations (id),
    amount NUMERIC(19,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    reason VARCHAR(300) NOT NULL,
    payment_method VARCHAR(50),
    bank_account_id UUID NOT NULL REFERENCES bank_accounts (id),
    journal_entry_id UUID REFERENCES journal_entries (id),
    notes TEXT,
    created_by UUID NOT NULL REFERENCES persons (id),
    voided_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_refunds_amount CHECK (amount > 0),
    CONSTRAINT uq_refunds_number UNIQUE (company_id, refund_number),
    CONSTRAINT uq_refunds_journal UNIQUE (journal_entry_id)
);
CREATE INDEX idx_refunds_client ON refunds (company_id, client_id, refund_date DESC);

-- 4. Time entries / billing -------------------------------------------
CREATE TABLE time_entries (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies (id),
    entry_number VARCHAR(32) NOT NULL,
    case_file_id UUID NOT NULL REFERENCES cases (id),
    lawyer_id UUID NOT NULL REFERENCES persons (id),
    work_date DATE NOT NULL,
    duration_minutes INTEGER NOT NULL,
    description VARCHAR(500) NOT NULL,
    billable BOOLEAN NOT NULL DEFAULT TRUE,
    hourly_rate NUMERIC(19,2) NOT NULL DEFAULT 0,
    amount NUMERIC(19,2) NOT NULL DEFAULT 0,
    invoice_id UUID REFERENCES invoices (id),
    journal_entry_id UUID REFERENCES journal_entries (id),
    voided_at TIMESTAMP,
    created_by UUID NOT NULL REFERENCES persons (id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_time_entries_duration CHECK (duration_minutes > 0),
    CONSTRAINT chk_time_entries_amount CHECK (amount >= 0),
    CONSTRAINT chk_time_entries_rate CHECK (hourly_rate >= 0),
    CONSTRAINT uq_time_entries_number UNIQUE (company_id, entry_number),
    CONSTRAINT uq_time_entries_journal UNIQUE (journal_entry_id)
);
CREATE INDEX idx_time_entries_case ON time_entries (company_id, case_file_id, work_date DESC);
CREATE INDEX idx_time_entries_unbilled ON time_entries (company_id) WHERE invoice_id IS NULL AND voided_at IS NULL;

-- 5. Recurring invoice templates ---------------------------------------
CREATE TABLE recurring_invoices (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies (id),
    client_id UUID NOT NULL REFERENCES persons (id),
    case_file_id UUID REFERENCES cases (id),
    description VARCHAR(500) NOT NULL,
    frequency VARCHAR(20) NOT NULL,
    interval_count INTEGER NOT NULL DEFAULT 1,
    start_date DATE NOT NULL,
    end_date DATE,
    next_run_date DATE NOT NULL,
    subtotal NUMERIC(19,2) NOT NULL,
    tax_rate NUMERIC(5,2) NOT NULL DEFAULT 0,
    tax_amount NUMERIC(19,2) NOT NULL DEFAULT 0,
    total NUMERIC(19,2) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    times_generated INTEGER NOT NULL DEFAULT 0,
    last_generated_date DATE,
    notes TEXT,
    created_by UUID NOT NULL REFERENCES persons (id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_recurring_frequency CHECK (frequency IN ('WEEKLY','MONTHLY','QUARTERLY','YEARLY')),
    CONSTRAINT chk_recurring_interval CHECK (interval_count > 0),
    CONSTRAINT chk_recurring_dates CHECK (end_date IS NULL OR end_date >= start_date),
    CONSTRAINT chk_recurring_subtotal CHECK (subtotal > 0),
    CONSTRAINT chk_recurring_tax_rate CHECK (tax_rate >= 0 AND tax_rate <= 100),
    CONSTRAINT chk_recurring_total CHECK (total > 0)
);
CREATE INDEX idx_recurring_invoices_due ON recurring_invoices (company_id, next_run_date) WHERE active;

-- 6. Vendors and payables ----------------------------------------------
CREATE TABLE vendors (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies (id),
    name VARCHAR(200) NOT NULL,
    name_ar VARCHAR(200),
    email VARCHAR(200),
    phone VARCHAR(50),
    tax_number VARCHAR(50),
    address VARCHAR(300),
    currency VARCHAR(3) NOT NULL DEFAULT 'SAR',
    expense_account_id UUID REFERENCES accounts (id),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    notes TEXT,
    created_by UUID NOT NULL REFERENCES persons (id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_vendors_company_name UNIQUE (company_id, name)
);

CREATE TABLE vendor_bills (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies (id),
    bill_number VARCHAR(32) NOT NULL,
    vendor_id UUID NOT NULL REFERENCES vendors (id),
    bill_date DATE NOT NULL,
    due_date DATE,
    expense_account_id UUID NOT NULL REFERENCES accounts (id),
    subtotal NUMERIC(19,2) NOT NULL,
    tax_rate NUMERIC(5,2) NOT NULL DEFAULT 0,
    tax_amount NUMERIC(19,2) NOT NULL DEFAULT 0,
    total NUMERIC(19,2) NOT NULL,
    amount_paid NUMERIC(19,2) NOT NULL DEFAULT 0,
    status VARCHAR(25) NOT NULL DEFAULT 'OPEN',
    reference VARCHAR(100),
    journal_entry_id UUID REFERENCES journal_entries (id),
    notes TEXT,
    created_by UUID NOT NULL REFERENCES persons (id),
    voided_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_vendor_bills_subtotal CHECK (subtotal > 0),
    CONSTRAINT chk_vendor_bills_tax_rate CHECK (tax_rate >= 0 AND tax_rate <= 100),
    CONSTRAINT chk_vendor_bills_total CHECK (total > 0),
    CONSTRAINT chk_vendor_bills_paid CHECK (amount_paid >= 0),
    CONSTRAINT chk_vendor_bills_status CHECK (status IN ('OPEN','PARTIALLY_PAID','PAID','CANCELLED')),
    CONSTRAINT uq_vendor_bills_number UNIQUE (company_id, bill_number),
    CONSTRAINT uq_vendor_bills_journal UNIQUE (journal_entry_id)
);
CREATE INDEX idx_vendor_bills_vendor ON vendor_bills (company_id, vendor_id, bill_date DESC);

CREATE TABLE vendor_payments (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies (id),
    bill_id UUID NOT NULL REFERENCES vendor_bills (id),
    payment_date DATE NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    method VARCHAR(50),
    bank_account_id UUID NOT NULL REFERENCES bank_accounts (id),
    reference VARCHAR(100),
    journal_entry_id UUID REFERENCES journal_entries (id),
    notes TEXT,
    created_by UUID NOT NULL REFERENCES persons (id),
    voided_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_vendor_payments_amount CHECK (amount > 0),
    CONSTRAINT uq_vendor_payments_journal UNIQUE (journal_entry_id)
);
CREATE INDEX idx_vendor_payments_bill ON vendor_payments (company_id, bill_id);

-- 7. Bank reconciliation -------------------------------------------------
CREATE TABLE bank_reconciliations (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies (id),
    bank_account_id UUID NOT NULL REFERENCES bank_accounts (id),
    statement_date DATE NOT NULL,
    statement_closing_balance NUMERIC(19,2) NOT NULL,
    reconciled_balance NUMERIC(19,2) NOT NULL DEFAULT 0,
    difference NUMERIC(19,2) NOT NULL DEFAULT 0,
    status VARCHAR(25) NOT NULL DEFAULT 'IN_PROGRESS',
    completed_at TIMESTAMP,
    completed_by UUID REFERENCES persons (id),
    notes TEXT,
    created_by UUID NOT NULL REFERENCES persons (id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_reconciliation_status CHECK (status IN ('IN_PROGRESS','COMPLETED','CANCELLED')),
    CONSTRAINT uq_reconciliation_statement
        UNIQUE (company_id, bank_account_id, statement_date)
);

CREATE TABLE bank_statement_lines (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies (id),
    bank_account_id UUID NOT NULL REFERENCES bank_accounts (id),
    reconciliation_id UUID REFERENCES bank_reconciliations (id),
    line_date DATE NOT NULL,
    description VARCHAR(300),
    reference VARCHAR(100),
    amount NUMERIC(19,2) NOT NULL,
    matched_type VARCHAR(25),
    matched_id UUID,
    matched_at TIMESTAMP,
    notes TEXT,
    created_by UUID NOT NULL REFERENCES persons (id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_statement_line_amount CHECK (amount <> 0),
    CONSTRAINT chk_statement_line_match CHECK (
        (matched_type IS NULL AND matched_id IS NULL)
        OR (matched_type IN ('RECEIPT','PAYMENT','TRANSFER','EXPENSE') AND matched_id IS NOT NULL)
    )
);
CREATE INDEX idx_statement_lines_account ON bank_statement_lines (company_id, bank_account_id, line_date);
CREATE INDEX idx_statement_lines_unmatched ON bank_statement_lines (company_id) WHERE matched_id IS NULL;

-- 8. Budgets -------------------------------------------------------------
CREATE TABLE budgets (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies (id),
    name VARCHAR(200) NOT NULL,
    fiscal_year INTEGER NOT NULL,
    period VARCHAR(20) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    account_id UUID REFERENCES accounts (id),
    budget_amount NUMERIC(19,2) NOT NULL,
    notes TEXT,
    created_by UUID NOT NULL REFERENCES persons (id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_budgets_year CHECK (fiscal_year BETWEEN 2000 AND 2100),
    CONSTRAINT chk_budgets_period CHECK (period IN ('MONTH','QUARTER','YEAR','CUSTOM')),
    CONSTRAINT chk_budgets_dates CHECK (period_end >= period_start),
    CONSTRAINT chk_budgets_amount CHECK (budget_amount >= 0),
    CONSTRAINT uq_budgets_scope UNIQUE (company_id, name, period_start, period_end)
);