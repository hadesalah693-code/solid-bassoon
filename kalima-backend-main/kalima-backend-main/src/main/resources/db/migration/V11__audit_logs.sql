-- Audit Log: append-only record of sensitive actions.
--
-- Design notes:
--  * company_id may be NULL for system-level events (e.g. a failed login
--    attempt for an email that does not exist in any tenant). Tenant-scoped
--    reads via API never expose those rows.
--  * actor_person_id may be NULL for the same reason (pre-auth events).
--  * actor_email is copied at write-time so the record survives a later
--    delete of the actor row (forensic durability).
--  * metadata is a free-form JSON string (stored as TEXT to avoid a
--    PostgreSQL-specific type and keep the Hibernate mapping simple).
--  * success=false + error_message capture failed operations.
--  * There are no UPDATE / DELETE paths: audit logs are immutable.

CREATE TABLE audit_logs (
    id              UUID PRIMARY KEY,
    company_id      UUID         REFERENCES companies(id),
    actor_person_id UUID         REFERENCES persons(id),
    actor_email     VARCHAR(150),
    action          VARCHAR(100) NOT NULL,
    entity_type     VARCHAR(50),
    entity_id       UUID,
    ip_address      VARCHAR(45),
    user_agent      VARCHAR(500),
    metadata        TEXT,
    success         BOOLEAN      NOT NULL DEFAULT TRUE,
    error_message   TEXT,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Common read patterns: "all logs for my tenant, newest first",
-- "what did this user do", "find an entity's history", "every failed login".
CREATE INDEX idx_audit_company_created ON audit_logs(company_id, created_at DESC);
CREATE INDEX idx_audit_actor_created   ON audit_logs(actor_person_id, created_at DESC);
CREATE INDEX idx_audit_action          ON audit_logs(action);
CREATE INDEX idx_audit_entity          ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_success         ON audit_logs(success) WHERE success = FALSE;
