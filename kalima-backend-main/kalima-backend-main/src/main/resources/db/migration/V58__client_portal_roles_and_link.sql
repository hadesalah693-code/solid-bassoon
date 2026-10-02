-- Client Portal (Phase 1): portal identity & client linkage.
--
-- Model:
--   Company (tenant)
--      └─ persons.system_role = 'CLIENT'            (firm-side client profile)
--      └─ persons.system_role = 'CLIENT_USER'       (portal login, linked below)
--          └─ portal_client_id → persons.id (the linked CLIENT profile)
--
-- A portal login is a "CLIENT_USER" Person (login-capable like staff) that is
-- tied 1:1 (per tenant) to exactly one CLIENT profile row. The portal user
-- authenticates through the existing JWT pipeline and gets no additional powers;
-- every portal read is scoped through the linked client via the portal layer.

-- 1) Extend the allowed system_role values: existing staff roles + CLIENT + new CLIENT_USER.
ALTER TABLE persons DROP CONSTRAINT IF EXISTS chk_persons_system_role;
ALTER TABLE persons DROP CONSTRAINT IF EXISTS persons_system_role_check;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_constraint
               WHERE conrelid = 'persons'::regclass
                 AND contype = 'c'
                 AND conname = 'persons_system_role_check') THEN
        ALTER TABLE persons DROP CONSTRAINT persons_system_role_check;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_constraint
               WHERE conrelid = 'persons'::regclass
                 AND contype = 'c'
                 AND conname = 'chk_persons_system_role') THEN
        ALTER TABLE persons DROP CONSTRAINT chk_persons_system_role;
    END IF;
END $$;

ALTER TABLE persons ADD CONSTRAINT chk_persons_system_role CHECK (
    system_role IN ('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT','CLIENT','CLIENT_USER')
);

-- 2) Link column: portal login ⇒ its CLIENT profile.
--    Portal user must be INDIVIDUAL + active + email/password (login-capable),
--    exactly like staff — so reuse/relax the existing staff-requirements check.
ALTER TABLE persons ADD COLUMN portal_client_id UUID;

ALTER TABLE persons DROP CONSTRAINT IF EXISTS chk_persons_staff_requirements;
ALTER TABLE persons ADD CONSTRAINT chk_persons_staff_requirements CHECK (
    (system_role = 'CLIENT')
    OR (
        system_role = 'CLIENT_USER'
        AND person_type = 'INDIVIDUAL'
        AND email IS NOT NULL
        AND password_hash IS NOT NULL
        AND portal_client_id IS NOT NULL
    )
    OR (
        system_role NOT IN ('CLIENT','CLIENT_USER')
        AND person_type = 'INDIVIDUAL'
        AND email IS NOT NULL
        AND password_hash IS NOT NULL
    )
);

-- A CLIENT_USER may only be linked to another person of role CLIENT (the firm's
-- client profile), and only within the same tenant. PostgreSQL forbids subqueries
-- inside CHECK constraints ("cannot use subquery in check constraint"), so both
-- rules are enforced by a BEFORE INSERT/UPDATE trigger instead.
CREATE OR REPLACE FUNCTION chk_persons_portal_client_link() RETURNS trigger AS $$
DECLARE
    linked_role TEXT;
    linked_company UUID;
BEGIN
    IF NEW.portal_client_id IS NULL THEN
        RETURN NEW;
    END IF;

    SELECT p2.system_role, p2.company_id
      INTO linked_role, linked_company
      FROM persons p2
     WHERE p2.id = NEW.portal_client_id;

    IF linked_role IS NULL THEN
        RAISE EXCEPTION 'portal_client_id % does not reference an existing person', NEW.portal_client_id;
    END IF;
    IF linked_role <> 'CLIENT' THEN
        RAISE EXCEPTION 'portal_client_id must reference a CLIENT profile, got %', linked_role;
    END IF;
    IF linked_company IS DISTINCT FROM NEW.company_id THEN
        RAISE EXCEPTION 'portal_client_id must belong to the same company';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_persons_portal_client_link ON persons;
CREATE TRIGGER trg_persons_portal_client_link
    BEFORE INSERT OR UPDATE OF portal_client_id, company_id, system_role ON persons
    FOR EACH ROW EXECUTE FUNCTION chk_persons_portal_client_link();

-- FK + tenant-scoped uniqueness: at most ONE portal user per CLIENT within a tenant,
-- which also guarantees cross-tenant isolation of the link.
ALTER TABLE persons ADD CONSTRAINT fk_persons_portal_client
    FOREIGN KEY (portal_client_id) REFERENCES persons(id);

CREATE UNIQUE INDEX uq_persons_portal_client_per_tenant
    ON persons(company_id, portal_client_id)
    WHERE portal_client_id IS NOT NULL;
