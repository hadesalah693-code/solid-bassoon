-- Step 1: copy every row in `clients` into `persons` as system_role = 'CLIENT'.
-- Each client becomes a person that may later be linked to a portal account.
INSERT INTO persons (
    id, person_type, system_role,
    full_name, company_name, national_id, commercial_register,
    phone, email, address, notes,
    password_hash, is_active, last_login_at,
    created_at, updated_at
)
SELECT
    c.id,
    c.client_type,
    'CLIENT',
    c.full_name,
    c.company_name,
    c.national_id,
    c.commercial_register,
    c.phone,
    c.email,
    c.address,
    c.notes,
    NULL,
    TRUE,
    NULL,
    c.created_at,
    c.updated_at
FROM clients c;

-- Step 2: a legacy `users` row with role = 'CLIENT' is the portal account of an existing client.
-- Merge its credentials into the corresponding person (matched by users.client_id).
UPDATE persons p
SET
    password_hash = u.password_hash,
    is_active = u.is_active,
    email = COALESCE(p.email, u.email)
FROM users u
WHERE u.role = 'CLIENT'
  AND u.client_id = p.id;

-- Step 3: copy every staff user (role <> 'CLIENT') into `persons` as its own row.
-- Staff is always an INDIVIDUAL; company-specific fields stay NULL.
INSERT INTO persons (
    id, person_type, system_role,
    full_name, company_name, national_id, commercial_register,
    phone, email, address, notes,
    password_hash, is_active, last_login_at,
    created_at, updated_at
)
SELECT
    u.id,
    'INDIVIDUAL',
    u.role,
    u.full_name,
    NULL,
    NULL,
    NULL,
    u.phone,
    u.email,
    NULL,
    NULL,
    u.password_hash,
    u.is_active,
    NULL,
    u.created_at,
    u.updated_at
FROM users u
WHERE u.role <> 'CLIENT';
