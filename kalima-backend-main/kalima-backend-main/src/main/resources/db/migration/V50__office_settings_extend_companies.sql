-- Office settings: extend tenant profile fields used on letterheads and forms.

ALTER TABLE companies
    ADD COLUMN IF NOT EXISTS website VARCHAR(200);

ALTER TABLE companies
    ADD COLUMN IF NOT EXISTS city VARCHAR(100);

ALTER TABLE companies
    ADD COLUMN IF NOT EXISTS country_code VARCHAR(2) NOT NULL DEFAULT 'SA';
