-- A cash account must back at most one bank account, otherwise per-bank movement
-- and balance would double-count every posted line on that ledger account.
CREATE UNIQUE INDEX IF NOT EXISTS ux_bank_accounts_company_account
    ON bank_accounts (company_id, account_id);
