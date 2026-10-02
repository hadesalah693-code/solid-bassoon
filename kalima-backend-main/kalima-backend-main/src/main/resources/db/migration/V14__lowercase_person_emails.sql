-- Data-only: store person emails in canonical lowercase so lookups stay consistent
-- (no DDL change; unique constraint on persons.email unchanged)

UPDATE persons
SET email = LOWER(TRIM(email))
WHERE email IS NOT NULL
  AND email <> LOWER(TRIM(email));
