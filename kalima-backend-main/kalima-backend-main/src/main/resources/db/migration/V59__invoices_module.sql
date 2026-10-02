-- V59__invoices_module.sql
-- وحدة التحصيل (Invoices) — فواتير مرتبطة بقضية وعميل، مع عدّاد ترقيم سنوي
-- لكل شركة (INV-YYYY-xxxxx). يطابق كيانات Invoice و InvoiceNumberSequence،
-- بما في ذلك عدّاد @Filter(name = "tenantFilter") على company_id.

CREATE TABLE invoice_number_sequences (
    id          UUID    PRIMARY KEY,
    company_id  UUID    NOT NULL REFERENCES companies (id),
    year        INTEGER NOT NULL,
    last_value  BIGINT  NOT NULL DEFAULT 0,
    CONSTRAINT uq_invoice_number_sequences_company_year UNIQUE (company_id, year)
);

CREATE TABLE invoices (
    id              UUID          PRIMARY KEY,
    company_id      UUID          NOT NULL REFERENCES companies (id),
    recorded_by     UUID          NOT NULL REFERENCES persons (id),
    client_id       UUID          NOT NULL REFERENCES persons (id),
    case_id         UUID          REFERENCES cases (id),
    invoice_number  VARCHAR(32)   NOT NULL,
    issue_date      DATE          NOT NULL,
    due_date        DATE,
    subtotal        NUMERIC(19,2) NOT NULL DEFAULT 0,
    tax_rate        NUMERIC(5,2),
    tax_amount      NUMERIC(19,2),
    discount        NUMERIC(19,2),
    total           NUMERIC(19,2) NOT NULL DEFAULT 0,
    amount_paid     NUMERIC(19,2) NOT NULL DEFAULT 0,
    status          VARCHAR(25)   NOT NULL,
    notes           TEXT,
    created_at      TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_invoices_invoice_number UNIQUE (invoice_number),
    CONSTRAINT chk_invoices_status CHECK (
        status IN ('DRAFT', 'ISSUED', 'PARTIALLY_PAID', 'PAID', 'OVERDUE', 'CANCELLED')
    ),
    CONSTRAINT chk_invoices_subtotal_non_negative CHECK (subtotal >= 0),
    CONSTRAINT chk_invoices_discount_non_negative CHECK (discount IS NULL OR discount >= 0),
    CONSTRAINT chk_invoices_paid_non_negative CHECK (amount_paid >= 0),
    CONSTRAINT chk_invoices_paid_lte_total CHECK (amount_paid <= total)
);

CREATE INDEX idx_invoices_company ON invoices (company_id);
CREATE INDEX idx_invoices_status ON invoices (company_id, status);
CREATE INDEX idx_invoices_client ON invoices (client_id);
CREATE INDEX idx_invoices_case ON invoices (case_id);
CREATE INDEX idx_invoices_due_date ON invoices (company_id, due_date);
