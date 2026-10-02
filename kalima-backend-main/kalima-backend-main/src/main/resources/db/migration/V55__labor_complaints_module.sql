-- Labor complaints module (الشكاوى العمالية) — tenant-scoped lifecycle tracking.

CREATE TABLE labor_complaint_number_counters (
    company_id    UUID    NOT NULL,
    year          INTEGER NOT NULL,
    next_number   INTEGER NOT NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_labor_complaint_number_counters PRIMARY KEY (company_id, year),
    CONSTRAINT fk_lcnc_company FOREIGN KEY (company_id) REFERENCES companies (id)
);

CREATE TABLE labor_complaints (
    id                      UUID PRIMARY KEY,
    company_id              UUID         NOT NULL REFERENCES companies (id),
    complaint_number        VARCHAR(32)  NOT NULL,
    complaint_year          INTEGER      NOT NULL,
    registration_date       DATE         NOT NULL,
    receipt_date            DATE         NOT NULL,
    subject                 TEXT,
    summary                 TEXT,
    legal_request           TEXT,
    status                  VARCHAR(40)  NOT NULL,
    client_id               UUID         NOT NULL REFERENCES persons (id),
    assigned_lawyer_id      UUID         REFERENCES persons (id),
    opponent_name           VARCHAR(250),
    opponent_capacity       VARCHAR(60),
    saved_reason            TEXT,
    saved_at                TIMESTAMP,
    referred_at             TIMESTAMP,
    referral_notes          TEXT,
    linked_case_id          UUID         REFERENCES cases (id),
    linked_dispute_id       UUID         REFERENCES disputes (id),
    internal_reference      VARCHAR(250),
    external_partners       TEXT,
    opponent_representative VARCHAR(250),
    cooperating_offices     TEXT,
    created_by              UUID         NOT NULL REFERENCES persons (id),
    created_at              TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_labor_complaints_company_number UNIQUE (company_id, complaint_number),
    CONSTRAINT chk_labor_complaints_status CHECK (
        status IN ('ACTIVE', 'SAVED', 'REFERRAL_TO_COMMITTEE')
    )
);

CREATE INDEX idx_labor_complaints_company ON labor_complaints (company_id);
CREATE INDEX idx_labor_complaints_status ON labor_complaints (company_id, status);
CREATE INDEX idx_labor_complaints_client ON labor_complaints (client_id);
CREATE INDEX idx_labor_complaints_registration ON labor_complaints (company_id, registration_date DESC);

CREATE TABLE labor_complaint_parties (
    id                  UUID PRIMARY KEY,
    company_id          UUID         NOT NULL REFERENCES companies (id),
    labor_complaint_id  UUID         NOT NULL REFERENCES labor_complaints (id) ON DELETE CASCADE,
    party_kind          VARCHAR(20)  NOT NULL,
    person_id           UUID         REFERENCES persons (id),
    display_name        VARCHAR(250),
    capacity            VARCHAR(60),
    sort_order          INTEGER      NOT NULL DEFAULT 0,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_labor_complaint_parties_kind CHECK (party_kind IN ('CLIENT', 'OPPONENT')),
    CONSTRAINT chk_labor_complaint_parties_shape CHECK (
        (party_kind = 'CLIENT' AND person_id IS NOT NULL)
            OR (party_kind = 'OPPONENT' AND display_name IS NOT NULL AND length(trim(display_name)) > 0)
    )
);

CREATE INDEX idx_labor_complaint_parties_report ON labor_complaint_parties (labor_complaint_id);
CREATE INDEX idx_labor_complaint_parties_person ON labor_complaint_parties (person_id);
CREATE INDEX idx_labor_complaint_parties_company ON labor_complaint_parties (company_id);

CREATE TABLE labor_complaint_mol_sessions (
    id                  UUID PRIMARY KEY,
    company_id          UUID         NOT NULL REFERENCES companies (id),
    labor_complaint_id  UUID         NOT NULL REFERENCES labor_complaints (id) ON DELETE CASCADE,
    session_date        TIMESTAMP    NOT NULL,
    location            VARCHAR(250),
    notes               TEXT,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_labor_complaint_mol_sessions ON labor_complaint_mol_sessions (labor_complaint_id);

CREATE TABLE labor_complaint_documents (
    id                  UUID PRIMARY KEY,
    company_id          UUID         NOT NULL REFERENCES companies (id),
    labor_complaint_id  UUID         NOT NULL REFERENCES labor_complaints (id) ON DELETE CASCADE,
    uploaded_by         UUID         NOT NULL REFERENCES persons (id),
    original_filename   VARCHAR(255) NOT NULL,
    stored_filename     VARCHAR(255) NOT NULL,
    content_type        VARCHAR(150) NOT NULL,
    size_bytes          BIGINT       NOT NULL,
    description         TEXT,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_labor_complaint_documents ON labor_complaint_documents (labor_complaint_id, created_at DESC);

ALTER TABLE tasks ADD COLUMN labor_complaint_id UUID REFERENCES labor_complaints (id);
CREATE INDEX idx_tasks_labor_complaint ON tasks (company_id, labor_complaint_id);
