-- Ensure production admin login matches INIT_ADMIN_* env (admin@kalima.com / KalimaAdmin2026!).
-- BCrypt hash generated for KalimaAdmin2026! (strength 10). Idempotent upsert by email.

INSERT INTO persons (
    id,
    company_id,
    person_type,
    system_role,
    full_name,
    email,
    password_hash,
    is_active,
    created_at,
    updated_at
)
VALUES (
    gen_random_uuid(),
    '00000000-0000-0000-0000-000000000001',
    'INDIVIDUAL',
    'ADMIN',
    'مدير النظام',
    'admin@kalima.com',
    '$2b$10$hFNxJFj.VgxwszkLziI3XOhs8Nc60/RcPsicvsIAOa.AA/o/RTkru',
    TRUE,
    NOW(),
    NOW()
)
ON CONFLICT (email) DO UPDATE
SET password_hash = EXCLUDED.password_hash,
    is_active     = TRUE,
    archived_at   = NULL,
    system_role   = 'ADMIN',
    full_name     = EXCLUDED.full_name,
    updated_at    = NOW();
