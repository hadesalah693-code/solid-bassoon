-- Multi-tenancy foundation — the root tenant table.
-- Every other business table will later reference companies(id) via company_id.

CREATE TABLE companies (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    legal_name VARCHAR(250),
    commercial_register VARCHAR(100),
    tax_number VARCHAR(100),
    phone VARCHAR(30),
    email VARCHAR(150),
    address TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_companies_active ON companies(is_active);

-- Seed the default company so existing rows (from V1-V4) have a valid tenant
-- to be backfilled to in V7. Fixed UUID keeps migrations idempotent and
-- readable. Do NOT change this UUID.
INSERT INTO companies (id, name, legal_name, is_active)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'المكتب الافتراضي',
    'Kalima Default Company',
    TRUE
);
