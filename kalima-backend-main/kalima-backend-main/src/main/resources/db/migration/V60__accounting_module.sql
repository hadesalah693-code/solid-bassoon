-- Full accounting module: chart of accounts, double-entry journal, expenses,
-- receipts, bank accounts, financial periods and tax rates.

-- ---------------------------------------------------------------------------
-- Chart of accounts
-- ---------------------------------------------------------------------------
CREATE TABLE accounts (
    id              UUID            PRIMARY KEY,
    company_id      UUID            NOT NULL REFERENCES companies (id),
    code            VARCHAR(20)     NOT NULL,
    name_ar         VARCHAR(150)    NOT NULL,
    name_en         VARCHAR(150),
    type            VARCHAR(20)     NOT NULL,
    parent_id       UUID            REFERENCES accounts (id),
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
    is_system       BOOLEAN         NOT NULL DEFAULT FALSE,
    description     TEXT,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_accounts_code UNIQUE (company_id, code),
    CONSTRAINT chk_accounts_type CHECK (
        type IN ('ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE')
    )
);

CREATE INDEX idx_accounts_company ON accounts (company_id);
CREATE INDEX idx_accounts_type ON accounts (company_id, type);

COMMENT ON TABLE accounts IS 'شجرة الحسابات / Chart of accounts';

-- ---------------------------------------------------------------------------
-- Bank & cash accounts (subledger of the ASSET type)
-- ---------------------------------------------------------------------------
CREATE TABLE bank_accounts (
    id              UUID            PRIMARY KEY,
    company_id      UUID            NOT NULL REFERENCES companies (id),
    account_id      UUID            NOT NULL REFERENCES accounts (id),
    name_ar         VARCHAR(150)    NOT NULL,
    bank_name       VARCHAR(150),
    iban            VARCHAR(50),
    account_number  VARCHAR(50),
    currency        VARCHAR(3)      NOT NULL DEFAULT 'SAR',
    opening_balance NUMERIC(19,2)   NOT NULL DEFAULT 0,
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_bank_accounts_opening_balance CHECK (opening_balance >= 0)
);

CREATE INDEX idx_bank_accounts_company ON bank_accounts (company_id);

COMMENT ON TABLE bank_accounts IS 'الخزائن والحسابات البنكية';

-- ---------------------------------------------------------------------------
-- Clients subledger (control account per client)
-- ---------------------------------------------------------------------------
CREATE TABLE client_accounts (
    id              UUID            PRIMARY KEY,
    company_id      UUID            NOT NULL REFERENCES companies (id),
    person_id       UUID            NOT NULL REFERENCES persons (id),
    account_id      UUID            NOT NULL REFERENCES accounts (id),
    opening_balance NUMERIC(19,2)   NOT NULL DEFAULT 0,
    credit_limit    NUMERIC(19,2),
    notes           TEXT,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_client_accounts_person UNIQUE (company_id, person_id)
);

CREATE INDEX idx_client_accounts_company ON client_accounts (company_id);

COMMENT ON TABLE client_accounts IS 'حسابات العملاء (مستحقات ومدفوعات)';

-- ---------------------------------------------------------------------------
-- Journal entries (double entry)
-- ---------------------------------------------------------------------------
CREATE TABLE journal_entries (
    id              UUID            PRIMARY KEY,
    company_id      UUID            NOT NULL REFERENCES companies (id),
    entry_number    VARCHAR(32)     NOT NULL,
    entry_date      DATE            NOT NULL,
    description     VARCHAR(500)    NOT NULL,
    reference_type  VARCHAR(30),
    reference_id    UUID,
    source          VARCHAR(30)     NOT NULL DEFAULT 'MANUAL',
    is_posted       BOOLEAN         NOT NULL DEFAULT TRUE,
    posted_at       TIMESTAMP,
    reversed_entry_id UUID          REFERENCES journal_entries (id),
    total_debit     NUMERIC(19,2)   NOT NULL DEFAULT 0,
    total_credit    NUMERIC(19,2)   NOT NULL DEFAULT 0,
    created_by      UUID            NOT NULL REFERENCES persons (id),
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_journal_entries_number UNIQUE (company_id, entry_number),
    CONSTRAINT chk_journal_entries_source CHECK (
        source IN ('MANUAL', 'INVOICE', 'PAYMENT', 'EXPENSE', 'RECEIPT', 'OPENING', 'REVERSAL')
    )
);

CREATE INDEX idx_journal_entries_company_date ON journal_entries (company_id, entry_date DESC);
CREATE INDEX idx_journal_entries_reference ON journal_entries (company_id, reference_type, reference_id);
CREATE INDEX idx_journal_entries_posted ON journal_entries (company_id, is_posted);

COMMENT ON TABLE journal_entries IS 'القيود اليومية';

CREATE TABLE journal_lines (
    id              UUID            PRIMARY KEY,
    company_id      UUID            NOT NULL REFERENCES companies (id),
    entry_id        UUID            NOT NULL REFERENCES journal_entries (id) ON DELETE CASCADE,
    line_no         INTEGER         NOT NULL,
    account_id      UUID            NOT NULL REFERENCES accounts (id),
    debit           NUMERIC(19,2)   NOT NULL DEFAULT 0,
    credit          NUMERIC(19,2)   NOT NULL DEFAULT 0,
    description     VARCHAR(500),
    client_id       UUID            REFERENCES persons (id),
    bank_account_id UUID            REFERENCES bank_accounts (id),
    CONSTRAINT chk_journal_lines_amount CHECK (
        (debit > 0 AND credit = 0) OR (credit > 0 AND debit = 0) OR (debit = 0 AND credit = 0)
    ),
    CONSTRAINT uq_journal_lines_entry_line UNIQUE (entry_id, line_no)
);

CREATE INDEX idx_journal_lines_entry ON journal_lines (entry_id);
CREATE INDEX idx_journal_lines_account ON journal_lines (company_id, account_id);
CREATE INDEX idx_journal_lines_client ON journal_lines (company_id, client_id);

COMMENT ON TABLE journal_lines IS 'سطور القيود اليومية (مدين / دائن)';

-- ---------------------------------------------------------------------------
-- Expenses
-- ---------------------------------------------------------------------------
CREATE TABLE expenses (
    id              UUID            PRIMARY KEY,
    company_id      UUID            NOT NULL REFERENCES companies (id),
    expense_number  VARCHAR(32)     NOT NULL,
    expense_date    DATE            NOT NULL,
    category        VARCHAR(50)     NOT NULL,
    payee           VARCHAR(200),
    description     VARCHAR(500)    NOT NULL,
    account_id      UUID            NOT NULL REFERENCES accounts (id),
    bank_account_id UUID            REFERENCES bank_accounts (id),
    client_id       UUID            REFERENCES persons (id),
    subtotal        NUMERIC(19,2)   NOT NULL DEFAULT 0,
    tax_rate        NUMERIC(5,2),
    tax_amount      NUMERIC(19,2)   NOT NULL DEFAULT 0,
    total           NUMERIC(19,2)   NOT NULL DEFAULT 0,
    payment_method  VARCHAR(50),
    reference       VARCHAR(100),
    is_recurring    BOOLEAN         NOT NULL DEFAULT FALSE,
    journal_entry_id UUID           REFERENCES journal_entries (id),
    notes           TEXT,
    recorded_by     UUID            NOT NULL REFERENCES persons (id),
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_expenses_number UNIQUE (company_id, expense_number),
    CONSTRAINT chk_expenses_total_non_negative CHECK (total >= 0),
    CONSTRAINT chk_expenses_category CHECK (
        category IN ('SALARIES', 'RENT', 'UTILITIES', 'MARKETING', 'SOFTWARE',
                     'GOVERNMENT_FEES', 'LEGAL_FEES', 'TRANSPORT', 'SUPPLIES', 'OTHER')
    )
);

CREATE INDEX idx_expenses_company_date ON expenses (company_id, expense_date DESC);
CREATE INDEX idx_expenses_category ON expenses (company_id, category);

COMMENT ON TABLE expenses IS 'المصروفات (رواتب، إيجار، خدمات، مصاريف مكتب)';

-- ---------------------------------------------------------------------------
-- Receipts (money in that is not an invoice settlement)
-- ---------------------------------------------------------------------------
CREATE TABLE receipts (
    id              UUID            PRIMARY KEY,
    company_id      UUID            NOT NULL REFERENCES companies (id),
    receipt_number  VARCHAR(32)     NOT NULL,
    receipt_date    DATE            NOT NULL,
    client_id       UUID            REFERENCES persons (id),
    bank_account_id UUID            NOT NULL REFERENCES bank_accounts (id),
    amount          NUMERIC(19,2)   NOT NULL,
    currency        VARCHAR(3)      NOT NULL DEFAULT 'SAR',
    payment_method  VARCHAR(50),
    reference       VARCHAR(100),
    is_deposit      BOOLEAN         NOT NULL DEFAULT FALSE,
    notes           TEXT,
    journal_entry_id UUID           REFERENCES journal_entries (id),
    recorded_by     UUID            NOT NULL REFERENCES persons (id),
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_receipts_number UNIQUE (company_id, receipt_number),
    CONSTRAINT chk_receipts_amount_positive CHECK (amount > 0)
);

CREATE INDEX idx_receipts_company_date ON receipts (company_id, receipt_date DESC);
CREATE INDEX idx_receipts_client ON receipts (company_id, client_id);

COMMENT ON TABLE receipts IS 'المقبوضات';

-- ---------------------------------------------------------------------------
-- Invoice payment rows (audit trail; invoices.amount_paid stays a cached total)
-- ---------------------------------------------------------------------------
CREATE TABLE invoice_payments (
    id              UUID            PRIMARY KEY,
    company_id      UUID            NOT NULL REFERENCES companies (id),
    invoice_id      UUID            NOT NULL REFERENCES invoices (id) ON DELETE CASCADE,
    amount          NUMERIC(19,2)   NOT NULL,
    payment_date    DATE            NOT NULL,
    method          VARCHAR(50),
    bank_account_id UUID            REFERENCES bank_accounts (id),
    reference       VARCHAR(100),
    notes           TEXT,
    journal_entry_id UUID           REFERENCES journal_entries (id),
    recorded_by     UUID            NOT NULL REFERENCES persons (id),
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_invoice_payments_amount_positive CHECK (amount > 0)
);

CREATE INDEX idx_invoice_payments_invoice ON invoice_payments (invoice_id);
CREATE INDEX idx_invoice_payments_company_date ON invoice_payments (company_id, payment_date DESC);

-- ---------------------------------------------------------------------------
-- Financial periods
-- ---------------------------------------------------------------------------
CREATE TABLE financial_periods (
    id              UUID            PRIMARY KEY,
    company_id      UUID            NOT NULL REFERENCES companies (id),
    name            VARCHAR(50)     NOT NULL,
    start_date      DATE            NOT NULL,
    end_date        DATE            NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'OPEN',
    closed_at       TIMESTAMP,
    closed_by       UUID            REFERENCES persons (id),
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_financial_periods_name UNIQUE (company_id, name),
    CONSTRAINT chk_financial_periods_dates CHECK (end_date >= start_date),
    CONSTRAINT chk_financial_periods_status CHECK (
        status IN ('OPEN', 'CLOSED', 'LOCKED')
    )
);

CREATE INDEX idx_financial_periods_company ON financial_periods (company_id, start_date DESC);

COMMENT ON TABLE financial_periods IS 'الفترات المالية';

-- ---------------------------------------------------------------------------
-- Tax rates
-- ---------------------------------------------------------------------------
CREATE TABLE tax_rates (
    id              UUID            PRIMARY KEY,
    company_id      UUID            NOT NULL REFERENCES companies (id),
    name_ar         VARCHAR(100)    NOT NULL,
    rate            NUMERIC(5,2)    NOT NULL DEFAULT 0,
    account_id      UUID            REFERENCES accounts (id),
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
    is_default      BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_tax_rates_rate CHECK (rate >= 0 AND rate <= 100)
);

CREATE INDEX idx_tax_rates_company ON tax_rates (company_id);

-- ---------------------------------------------------------------------------
-- Journal number sequences (JE-2026-00001)
-- ---------------------------------------------------------------------------
CREATE TABLE accounting_sequences (
    id          UUID            PRIMARY KEY,
    company_id  UUID            NOT NULL REFERENCES companies (id),
    scope       VARCHAR(20)     NOT NULL,
    year        INTEGER         NOT NULL,
    last_value  BIGINT          NOT NULL DEFAULT 0,
    CONSTRAINT uq_accounting_sequences UNIQUE (company_id, scope, year),
    CONSTRAINT chk_accounting_sequences_scope CHECK (
        scope IN ('JOURNAL', 'EXPENSE', 'RECEIPT')
    )
);

-- ---------------------------------------------------------------------------
-- Seed the chart of accounts + a default cash bank account for each existing company
-- ---------------------------------------------------------------------------
INSERT INTO accounts (id, company_id, code, name_ar, name_en, type, is_system, created_at, updated_at)
SELECT gen_random_uuid(), c.id, s.code, s.name_ar, s.name_en, s.type, TRUE, NOW(), NOW()
FROM companies c
CROSS JOIN (VALUES
    ('1000', 'الأصول',                              'Assets',                   'ASSET'),
    ('1100', 'النقدية في الصندوق',                   'Cash on Hand',             'ASSET'),
    ('1110', 'البنك',                                'Bank',                     'ASSET'),
    ('1200', 'ذمم العملاء',                          'Accounts Receivable',      'ASSET'),
    ('1500', 'مصروفات مدفوعة مقدماً',                'Prepaid Expenses',         'ASSET'),
    ('2000', 'الالتزامات',                           'Liabilities',              'LIABILITY'),
    ('2100', 'ذمم الموردين',                         'Accounts Payable',         'LIABILITY'),
    ('2200', 'ضريبة القيمة المضافة المستحقة',         'VAT Payable',              'LIABILITY'),
    ('2210', 'ضريبة القيمة المضافة على المشتريات',   'VAT Input (Recoverable)',  'ASSET'),
    ('3000', 'حقوق الملكية',                         'Equity',                   'EQUITY'),
    ('3100', 'رأس المال',                            'Capital',                  'EQUITY'),
    ('3200', 'المسحوبات',                            'Drawings',                 'EQUITY'),
    ('4000', 'الإيرادات',                            'Revenue',                  'REVENUE'),
    ('4100', 'إيرادات الأتعاب القانونية',            'Legal Fees Income',        'REVENUE'),
    ('4200', 'إيرادات الاستشارات',                   'Consulting Income',        'REVENUE'),
    ('4900', 'إيرادات أخرى',                         'Other Income',             'REVENUE'),
    ('5000', 'المصروفات',                            'Expenses',                 'EXPENSE'),
    ('5100', 'الرواتب والأجور',                      'Salaries & Wages',         'EXPENSE'),
    ('5200', 'الإيجار',                              'Rent',                     'EXPENSE'),
    ('5300', 'الخدمات والفواتير',                    'Utilities',                'EXPENSE'),
    ('5400', 'التسويق والإعلان',                     'Marketing',                'EXPENSE'),
    ('5500', 'البرمجيات والاشتراكات',               'Software & Subscriptions', 'EXPENSE'),
    ('5600', 'الرسوم الحكومية والقضائية',             'Government & Court Fees',  'EXPENSE'),
    ('5700', 'مصروفات قانونية',                      'Legal Fees Expense',       'EXPENSE'),
    ('5800', 'النقل والمواصلات',                     'Transport',                'EXPENSE'),
    ('5900', 'مستلزمات مكتب',                        'Office Supplies',          'EXPENSE'),
    ('5950', 'مصروفات متنوعة',                       'Other Expenses',           'EXPENSE')
) AS s (code, name_ar, name_en, type);

-- Default cash account so expenses/receipts can be posted right away.
INSERT INTO bank_accounts (id, company_id, account_id, name_ar, currency, opening_balance, is_active, created_at, updated_at)
SELECT gen_random_uuid(), c.id, a.id, 'الصندوق', 'SAR', 0, TRUE, NOW(), NOW()
FROM companies c
JOIN accounts a
  ON a.company_id = c.id
 AND a.code = '1100'
WHERE NOT EXISTS (SELECT 1 FROM bank_accounts b WHERE b.company_id = c.id);

-- Default tax rate (VAT 15%) for Saudi law firms.
INSERT INTO tax_rates (id, company_id, name_ar, rate, is_active, is_default, created_at, updated_at)
SELECT gen_random_uuid(), c.id, 'ضريبة القيمة المضافة 15%', 15, TRUE, TRUE, NOW(), NOW()
FROM companies c
WHERE NOT EXISTS (SELECT 1 FROM tax_rates t WHERE t.company_id = c.id);

-- Opening balance financial period for the current year.
INSERT INTO financial_periods (id, company_id, name, start_date, end_date, status, created_at, updated_at)
SELECT gen_random_uuid(), c.id, EXTRACT(YEAR FROM CURRENT_DATE)::TEXT,
       make_date(EXTRACT(YEAR FROM CURRENT_DATE)::INT, 1, 1),
       make_date(EXTRACT(YEAR FROM CURRENT_DATE)::INT, 12, 31),
       'OPEN', NOW(), NOW()
FROM companies c
WHERE NOT EXISTS (SELECT 1 FROM financial_periods p WHERE p.company_id = c.id);
