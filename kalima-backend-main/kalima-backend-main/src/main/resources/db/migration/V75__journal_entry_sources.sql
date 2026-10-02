-- V75: widen chk_journal_entries_source for the revenue-side and payables features.
--
-- JournalEntry.Source gained REFUND, CREDIT_NOTE, DEBIT_NOTE, TIME_BILLING,
-- RECURRING_INVOICE, VENDOR_BILL, VENDOR_PAYMENT and RECONCILIATION. The V60
-- CHECK hard-codes the original eight literals, so every entry posted by the new
-- features would be rejected by the database. Drop-and-recreate is the same
-- pattern V63 used for chk_accounting_sequences_scope.

ALTER TABLE journal_entries DROP CONSTRAINT IF EXISTS chk_journal_entries_source;
ALTER TABLE journal_entries ADD CONSTRAINT chk_journal_entries_source CHECK (
    source IN ('MANUAL', 'INVOICE', 'PAYMENT', 'EXPENSE', 'RECEIPT',
               'OPENING', 'REVERSAL', 'BANK_TRANSFER',
               'REFUND', 'CREDIT_NOTE', 'DEBIT_NOTE', 'TIME_BILLING',
               'RECURRING_INVOICE', 'VENDOR_BILL', 'VENDOR_PAYMENT',
               'RECONCILIATION')
);