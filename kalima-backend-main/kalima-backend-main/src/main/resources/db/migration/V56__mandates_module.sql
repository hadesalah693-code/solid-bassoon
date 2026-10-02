-- V56__mandates_module.sql
-- إنابات المحامين (Mandates) — tenant-scoped lifecycle tracking of lawyer
-- substitution in court sessions and cases.

CREATE TABLE mandate_number_counters (
    company_id   UUID     NOT NULL,
    year         INTEGER  NOT NULL,
    next_number  INTEGER  NOT NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_mandate_number_counters PRIMARY KEY (company_id, year),
    CONSTRAINT fk_mnc_company FOREIGN KEY (company_id) REFERENCES companies (id)
);

CREATE INDEX idx_manc_company ON mandate_number_counters (company_id);

CREATE TABLE mandates (
    id                      UUID          PRIMARY KEY,
    company_id              UUID          NOT NULL REFERENCES companies (id),
    mandate_number          VARCHAR(32)   NOT NULL,
    mandate_year            INTEGER       NOT NULL,
    case_id                 UUID          NOT NULL REFERENCES cases (id),
    hearing_id              UUID          REFERENCES hearings (id),
    client_id               UUID          NOT NULL REFERENCES persons (id),
    original_lawyer_id      UUID          NOT NULL REFERENCES persons (id),
    substituted_lawyer_id   UUID          NOT NULL REFERENCES persons (id),
    status                  VARCHAR(20)   NOT NULL,
    priority                VARCHAR(20)   NOT NULL,
    session_date            TIMESTAMP,
    court_name              VARCHAR(200),
    court_circuit           VARCHAR(100),
    courtroom               VARCHAR(100),
    reason                  TEXT,
    notes                   TEXT,
    instructions            TEXT,
    fees                    NUMERIC(19,2) NOT NULL DEFAULT 0,
    currency                VARCHAR(3)    NOT NULL DEFAULT 'SAR',
    payment_status          VARCHAR(20)   NOT NULL,
    amount_paid             NUMERIC(19,2) NOT NULL DEFAULT 0,
    cancelled_reason        VARCHAR(500),
    session_result          TEXT,
    court_decision          TEXT,
    next_session_date       TIMESTAMP,
    next_session_notes      TEXT,
    did_attend              BOOLEAN,
    closed_at               TIMESTAMP,
    created_by              UUID          NOT NULL REFERENCES persons (id),
    created_at              TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_mandates_company_number UNIQUE (company_id, mandate_number),
    CONSTRAINT chk_mandates_status CHECK (
        status IN ('PENDING', 'CONFIRMED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')
    ),
    CONSTRAINT chk_mandates_priority CHECK (
        priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')
    ),
    CONSTRAINT chk_mandates_payment_status CHECK (
        payment_status IN ('UNPAID', 'PARTIALLY_PAID', 'PAID')
    ),
    CONSTRAINT chk_mandates_fees_non_negative CHECK (fees >= 0),
    CONSTRAINT chk_mandates_paid_non_negative CHECK (amount_paid >= 0),
    CONSTRAINT chk_mandates_paid_lte_fees CHECK (amount_paid <= fees),
    CONSTRAINT chk_mandates_distinct_lawyers CHECK (original_lawyer_id <> substituted_lawyer_id)
);

CREATE INDEX idx_mandates_company ON mandates (company_id);
CREATE INDEX idx_mandates_status ON mandates (company_id, status);
CREATE INDEX idx_mandates_client ON mandates (client_id);
CREATE INDEX idx_mandates_case ON mandates (case_id);
CREATE INDEX idx_mandates_original_lawyer ON mandates (original_lawyer_id);
CREATE INDEX idx_mandates_substituted_lawyer ON mandates (substituted_lawyer_id);
CREATE INDEX idx_mandates_session_date ON mandates (company_id, session_date);

CREATE TABLE mandate_documents (
    id                  UUID          PRIMARY KEY,
    company_id          UUID          NOT NULL REFERENCES companies (id),
    mandate_id          UUID          NOT NULL REFERENCES mandates (id) ON DELETE CASCADE,
    uploaded_by         UUID          NOT NULL REFERENCES persons (id),
    original_filename   VARCHAR(255)  NOT NULL,
    stored_filename     VARCHAR(255)  NOT NULL,
    content_type        VARCHAR(150)  NOT NULL,
    size_bytes          BIGINT        NOT NULL,
    description         TEXT,
    document_type       VARCHAR(30)   NOT NULL,
    created_at          TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_mandate_documents ON mandate_documents (mandate_id, created_at DESC);

CREATE TABLE mandate_payments (
    id            UUID          PRIMARY KEY,
    company_id    UUID          NOT NULL REFERENCES companies (id),
    mandate_id    UUID          NOT NULL REFERENCES mandates (id) ON DELETE CASCADE,
    amount        NUMERIC(19,2) NOT NULL,
    payment_date  DATE          NOT NULL,
    method        VARCHAR(50),
    notes         TEXT,
    recorded_by   UUID          NOT NULL REFERENCES persons (id),
    created_at    TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_mandate_payments_amount_positive CHECK (amount > 0)
);

CREATE INDEX idx_mandate_payments ON mandate_payments (mandate_id);

CREATE TABLE mandate_events (
    id            UUID         PRIMARY KEY,
    company_id    UUID         NOT NULL REFERENCES companies (id),
    mandate_id    UUID         NOT NULL REFERENCES mandates (id) ON DELETE CASCADE,
    event_type    VARCHAR(30)  NOT NULL,
    note          TEXT,
    recorded_by   UUID         NOT NULL REFERENCES persons (id),
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_mandate_events ON mandate_events (mandate_id, created_at);
