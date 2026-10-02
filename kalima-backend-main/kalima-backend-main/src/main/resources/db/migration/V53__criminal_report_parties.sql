-- Multiple clients and opponents per criminal report.

CREATE TABLE criminal_report_parties (
    id                 UUID PRIMARY KEY,
    company_id         UUID         NOT NULL REFERENCES companies (id),
    criminal_report_id UUID         NOT NULL REFERENCES criminal_reports (id) ON DELETE CASCADE,
    party_kind         VARCHAR(20)  NOT NULL,
    person_id          UUID         REFERENCES persons (id),
    display_name       VARCHAR(250),
    capacity           VARCHAR(60),
    sort_order         INTEGER      NOT NULL DEFAULT 0,
    created_at         TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_criminal_report_parties_kind CHECK (party_kind IN ('CLIENT', 'OPPONENT')),
    CONSTRAINT chk_criminal_report_parties_shape CHECK (
        (party_kind = 'CLIENT' AND person_id IS NOT NULL)
            OR (party_kind = 'OPPONENT' AND display_name IS NOT NULL AND length(trim(display_name)) > 0)
    )
);

CREATE INDEX idx_criminal_report_parties_report ON criminal_report_parties (criminal_report_id);
CREATE INDEX idx_criminal_report_parties_person ON criminal_report_parties (person_id);
CREATE INDEX idx_criminal_report_parties_company ON criminal_report_parties (company_id);

INSERT INTO criminal_report_parties (id, company_id, criminal_report_id, party_kind, person_id, sort_order, created_at, updated_at)
SELECT gen_random_uuid(), company_id, id, 'CLIENT', client_id, 0, NOW(), NOW()
FROM criminal_reports;

INSERT INTO criminal_report_parties (id, company_id, criminal_report_id, party_kind, display_name, capacity, sort_order, created_at, updated_at)
SELECT gen_random_uuid(), company_id, id, 'OPPONENT', opponent_name, opponent_capacity, 0, NOW(), NOW()
FROM criminal_reports
WHERE opponent_name IS NOT NULL AND length(trim(opponent_name)) > 0;
