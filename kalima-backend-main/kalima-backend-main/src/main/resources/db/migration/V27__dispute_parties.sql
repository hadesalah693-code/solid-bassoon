-- Additional parties on a dispute (beyond primary client_id + textual opponent).
-- Nullable person_id links to tenants; optional display_name captures free-text actors.

CREATE TABLE dispute_parties (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    dispute_id UUID NOT NULL,
    role VARCHAR(40) NOT NULL,
    person_id UUID,
    display_name VARCHAR(250),
    phone VARCHAR(80),
    notes TEXT,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_dp_company FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_dp_dispute FOREIGN KEY (dispute_id) REFERENCES disputes (id) ON DELETE CASCADE,
    CONSTRAINT fk_dp_person FOREIGN KEY (person_id) REFERENCES persons (id),
    CONSTRAINT chk_dp_person_or_name CHECK (
        person_id IS NOT NULL
            OR (
                display_name IS NOT NULL AND length(trim(display_name)) > 0)),
    CONSTRAINT chk_dp_co_client_requires_person CHECK (
        role <> 'CO_CLIENT' OR person_id IS NOT NULL)
);

CREATE INDEX idx_dp_company_id ON dispute_parties (company_id);
CREATE INDEX idx_dp_dispute_id ON dispute_parties (dispute_id);
CREATE INDEX idx_dp_company_dispute ON dispute_parties (company_id, dispute_id);

CREATE UNIQUE INDEX uq_dp_dispute_person_role
    ON dispute_parties (dispute_id, person_id, role)
    WHERE person_id IS NOT NULL;
