-- Delegation Settings: office attorneys (الوكلاء المعتمدون بالمكتب).
-- Lets a tenant pre-register the staff members who typically act as agents
-- on powers of attorney, so delegation creation can auto-fill/suggest the
-- agent instead of always requiring manual selection. Additive only.

CREATE TABLE delegation_office_attorneys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies (id),
    person_id UUID NOT NULL REFERENCES persons (id),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_delegation_office_attorneys_person UNIQUE (company_id, person_id)
);

CREATE INDEX idx_delegation_office_attorneys_company ON delegation_office_attorneys (company_id);
CREATE INDEX idx_delegation_office_attorneys_active ON delegation_office_attorneys (company_id, active);

-- Enforces "only one default attorney per office" at the database level
-- (defence-in-depth on top of the service-layer check-and-clear logic).
CREATE UNIQUE INDEX uq_delegation_office_attorneys_default
    ON delegation_office_attorneys (company_id)
    WHERE is_default = TRUE;
