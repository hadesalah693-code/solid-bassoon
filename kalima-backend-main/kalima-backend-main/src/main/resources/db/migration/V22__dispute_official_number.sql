-- Official dispute numbers: DIS-YYYY-NNNN per company per calendar year (created_at year for backfill).
-- Allocation uses dispute_number_counters with PostgreSQL INSERT ... ON CONFLICT (atomic).

CREATE TABLE dispute_number_counters (
    company_id UUID NOT NULL,
    year INTEGER NOT NULL,
    next_number INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_dispute_number_counters PRIMARY KEY (company_id, year),
    CONSTRAINT fk_dnc_company FOREIGN KEY (company_id) REFERENCES companies (id)
);

CREATE INDEX idx_dnc_company ON dispute_number_counters (company_id);

ALTER TABLE disputes ADD COLUMN dispute_number VARCHAR(32);

-- Backfill from created_at year; stable order: created_at, id
WITH ranked AS (
    SELECT d.id,
           EXTRACT(YEAR FROM d.created_at)::INTEGER AS y,
           ROW_NUMBER() OVER (
               PARTITION BY d.company_id, EXTRACT(YEAR FROM d.created_at)
               ORDER BY d.created_at, d.id
           ) AS rn
    FROM disputes d
)
UPDATE disputes d
SET dispute_number = 'DIS-' || ranked.y || '-' || LPAD(ranked.rn::TEXT, 4, '0')
FROM ranked
WHERE d.id = ranked.id;

-- Next sequence after highest existing number per (company, year)
INSERT INTO dispute_number_counters (company_id, year, next_number, created_at, updated_at)
SELECT company_id,
       y,
       MAX(rn) + 1,
       NOW(),
       NOW()
FROM (
    SELECT company_id,
           EXTRACT(YEAR FROM created_at)::INTEGER AS y,
           ROW_NUMBER() OVER (
               PARTITION BY company_id, EXTRACT(YEAR FROM created_at)
               ORDER BY created_at, id
           ) AS rn
    FROM disputes
) x
GROUP BY company_id, y;

ALTER TABLE disputes ALTER COLUMN dispute_number SET NOT NULL;

ALTER TABLE disputes
    ADD CONSTRAINT uq_disputes_company_dispute_number UNIQUE (company_id, dispute_number);
