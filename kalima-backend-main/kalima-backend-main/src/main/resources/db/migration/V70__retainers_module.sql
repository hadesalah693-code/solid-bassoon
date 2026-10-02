-- ---------------------------------------------------------------------------
-- V70 — Client Retainers / Advances
-- ---------------------------------------------------------------------------
-- Retainers are funds received from a client in advance of work.
-- They sit in a liability account (2300 "Client Retainers") until applied to
-- an invoice or expense. When applied, the retainer liability is reduced and
-- the corresponding revenue/expense account is recognised.
-- ---------------------------------------------------------------------------

CREATE TABLE client_retainers (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id          UUID            NOT NULL REFERENCES companies (id),
    client_id           UUID            NOT NULL REFERENCES persons (id),
    amount              NUMERIC(19,2)   NOT NULL CHECK (amount > 0),
    currency            CHAR(3)         NOT NULL DEFAULT 'SAR',
    status              VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE'
                            CHECK (status IN ('ACTIVE', 'FULLY_APPLIED', 'REFUNDED', 'CANCELLED')),
    received_date       DATE            NOT NULL,
    received_by         UUID            NOT NULL REFERENCES persons (id),
    reference           VARCHAR(100),
    notes               TEXT,
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_client_retainers_company_ref UNIQUE (company_id, reference)
);

CREATE INDEX idx_client_retainers_company_client ON client_retainers (company_id, client_id);
CREATE INDEX idx_client_retainers_status ON client_retainers (company_id, status);

CREATE TABLE retainer_applications (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id          UUID            NOT NULL REFERENCES companies (id),
    retainer_id         UUID            NOT NULL REFERENCES client_retainers (id) ON DELETE RESTRICT,
    applied_amount      NUMERIC(19,2)   NOT NULL CHECK (applied_amount > 0),
    applied_date        DATE            NOT NULL,
    applied_by          UUID            NOT NULL REFERENCES persons (id),
    target_type         VARCHAR(30)     NOT NULL
                            CHECK (target_type IN ('INVOICE', 'EXPENSE', 'BILL')),
    target_id           UUID            NOT NULL,
    journal_entry_id    UUID            REFERENCES journal_entries (id) ON DELETE SET NULL,
    notes               TEXT,
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_retainer_applications_entry UNIQUE (journal_entry_id)
);

CREATE INDEX idx_retainer_applications_retainer ON retainer_applications (retainer_id);
CREATE INDEX idx_retainer_applications_target ON retainer_applications (company_id, target_type, target_id);

-- Ensure the retainer liability account exists in the system catalog (code 2300)
-- V60 seeded it for companies existing then; V63/V64+ companies get it via seeder.
-- No DML here — the ChartOfAccountsSeeder guarantees account 2300 per company.

COMMENT ON TABLE client_retainers IS 'عمولة / سلفة عميل — مبلغ مستلم مقدماً من العميل';
COMMENT ON TABLE retainer_applications IS 'تطبيق السلفة على فاتورة أو مصروف — ينقل الرصيد من 2300 إلى الإيراد/المصروف';