-- Refresh token store for Automatic Token Rotation.
-- Tokens are stored by their SHA-256 hash only — the raw token lives solely in
-- the client's HttpOnly cookie and is never persisted in clear text.
--
-- family_id groups all rotation-linked tokens from a single login session,
-- enabling full-family revocation when token reuse is detected (theft signal).

CREATE TABLE refresh_tokens (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    person_id   UUID        NOT NULL,
    company_id  UUID        NOT NULL,
    family_id   UUID        NOT NULL,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,  -- SHA-256 hex of the opaque raw token
    used_at     TIMESTAMP,                    -- NULL = not yet consumed; set on rotation
    revoked_at  TIMESTAMP,                    -- set on logout or theft-detected family revocation
    expires_at  TIMESTAMP   NOT NULL,
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_rt_person  FOREIGN KEY (person_id)  REFERENCES persons   (id),
    CONSTRAINT fk_rt_company FOREIGN KEY (company_id) REFERENCES companies  (id)
);

CREATE INDEX idx_rt_token_hash ON refresh_tokens (token_hash);
CREATE INDEX idx_rt_family_id  ON refresh_tokens (family_id);
CREATE INDEX idx_rt_person_id  ON refresh_tokens (person_id);
-- Supports cleanup job: delete WHERE expires_at < NOW()
CREATE INDEX idx_rt_expires_at ON refresh_tokens (expires_at);
