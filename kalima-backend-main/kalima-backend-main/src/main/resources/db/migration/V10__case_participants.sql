-- Case Participants (rich case-person roles).
--
-- The existing cases.client_id / cases.assigned_lawyer_id columns stay as
-- denormalised shortcuts for the PRIMARY client and PRIMARY lawyer. This
-- table captures the full, richer list of people involved in each case:
-- secondary lawyers, opposing parties, witnesses, experts, judges, etc.
--
-- Uniqueness is (case_id, person_id, role): a single person may appear in
-- multiple roles (e.g. WITNESS + EXPERT) but not twice in the same role.

CREATE TABLE case_participants (
    id         UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies(id),
    case_id    UUID NOT NULL REFERENCES cases(id) ON DELETE CASCADE,
    person_id  UUID NOT NULL REFERENCES persons(id),
    role       VARCHAR(40) NOT NULL,
    notes      TEXT,
    created_by UUID REFERENCES persons(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_case_participants_case_person_role
        UNIQUE (case_id, person_id, role)
);

CREATE INDEX idx_cp_company ON case_participants(company_id);
CREATE INDEX idx_cp_case    ON case_participants(case_id);
CREATE INDEX idx_cp_person  ON case_participants(person_id);
CREATE INDEX idx_cp_role    ON case_participants(role);

-- Backfill: for every existing case seed a CLIENT participant and (if set)
-- an ASSIGNED_LAWYER participant, so the API is internally consistent from
-- day one and we never hand clients a case with an empty participants list.
INSERT INTO case_participants (id, company_id, case_id, person_id, role, created_at, updated_at)
SELECT gen_random_uuid(), c.company_id, c.id, c.client_id, 'CLIENT', NOW(), NOW()
FROM cases c
WHERE c.client_id IS NOT NULL;

INSERT INTO case_participants (id, company_id, case_id, person_id, role, created_at, updated_at)
SELECT gen_random_uuid(), c.company_id, c.id, c.assigned_lawyer_id, 'ASSIGNED_LAWYER', NOW(), NOW()
FROM cases c
WHERE c.assigned_lawyer_id IS NOT NULL;
