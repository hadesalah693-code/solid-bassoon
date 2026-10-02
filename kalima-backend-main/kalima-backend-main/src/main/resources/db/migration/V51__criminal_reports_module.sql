-- Criminal reports module (البلاغات الجنائية) — tenant-scoped lifecycle tracking.

CREATE TABLE criminal_report_number_counters (
    company_id UUID NOT NULL,
    year       INTEGER NOT NULL,
    next_number INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_criminal_report_number_counters PRIMARY KEY (company_id, year),
    CONSTRAINT fk_crnc_company FOREIGN KEY (company_id) REFERENCES companies (id)
);

CREATE TABLE criminal_reports (
    id                    UUID PRIMARY KEY,
    company_id            UUID         NOT NULL REFERENCES companies (id),
    report_number         VARCHAR(32)  NOT NULL,
    report_year           INTEGER      NOT NULL,
    registration_date     DATE         NOT NULL,
    incident_date         DATE,
    prosecution_office    VARCHAR(200),
    security_center       VARCHAR(200),
    charge                VARCHAR(500),
    subject               TEXT,
    summary               TEXT,
    status                VARCHAR(30)  NOT NULL,
    client_id             UUID         NOT NULL REFERENCES persons (id),
    assigned_lawyer_id    UUID         REFERENCES persons (id),
    opponent_name         VARCHAR(250),
    opponent_capacity     VARCHAR(60),
    archive_reason        TEXT,
    grievance_period_days INTEGER,
    grievance_deadline    DATE,
    archived_at           TIMESTAMP,
    final_archive_reason  TEXT,
    decision_issuer       VARCHAR(250),
    decision_number       VARCHAR(100),
    decision_received_date DATE,
    final_decision_basis  TEXT,
    appeal_period_days    INTEGER,
    final_decision_level  VARCHAR(100),
    final_archived_at     TIMESTAMP,
    linked_case_id        UUID         REFERENCES cases (id),
    linked_dispute_id     UUID         REFERENCES disputes (id),
    internal_source       VARCHAR(250),
    legal_consultant      VARCHAR(250),
    external_consultant   VARCHAR(250),
    partner_notes         TEXT,
    created_by            UUID         NOT NULL REFERENCES persons (id),
    created_at            TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_criminal_reports_company_number UNIQUE (company_id, report_number),
    CONSTRAINT chk_criminal_reports_status CHECK (
        status IN ('UNDER_INVESTIGATION', 'REFERRAL', 'ARCHIVED', 'FINAL_ARCHIVED')
    )
);

CREATE INDEX idx_criminal_reports_company ON criminal_reports (company_id);
CREATE INDEX idx_criminal_reports_status ON criminal_reports (company_id, status);
CREATE INDEX idx_criminal_reports_client ON criminal_reports (client_id);
CREATE INDEX idx_criminal_reports_registration ON criminal_reports (company_id, registration_date DESC);

CREATE TABLE criminal_report_locations (
    id                  UUID PRIMARY KEY,
    company_id          UUID         NOT NULL REFERENCES companies (id),
    criminal_report_id  UUID         NOT NULL REFERENCES criminal_reports (id) ON DELETE CASCADE,
    location_type       VARCHAR(100),
    location_description TEXT,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_criminal_report_locations_report ON criminal_report_locations (criminal_report_id);

CREATE TABLE criminal_report_investigation_sessions (
    id                  UUID PRIMARY KEY,
    company_id          UUID         NOT NULL REFERENCES companies (id),
    criminal_report_id  UUID         NOT NULL REFERENCES criminal_reports (id) ON DELETE CASCADE,
    session_date        TIMESTAMP    NOT NULL,
    location            VARCHAR(250),
    notes               TEXT,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_criminal_report_sessions_report ON criminal_report_investigation_sessions (criminal_report_id);
