-- Performance indexes identified during review (2026-06).
--
-- 1. disputes.created_at — ORDER BY in every search/list query.
-- 2. dispute_timeline_events (dispute_id, created_at) — composite for ordered fetch.
-- 3. dispute_administrative_updates (dispute_id, update_type) — existsBy check.
-- 4. cases (company_id, status) — composite for searchWithoutText filter.
-- 5. persons (company_id, system_role) — role-filtered listing.
-- 6. pg_trgm GIN indexes — enable fast LIKE '%term%' on text search columns.

-- ─── 1. disputes.created_at ───────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_disputes_created_at
    ON disputes (created_at DESC);

-- ─── 2. dispute_timeline_events composite ─────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_dte_dispute_created
    ON dispute_timeline_events (dispute_id, created_at DESC);

-- ─── 3. dispute_administrative_updates composite ──────────────────────────
CREATE INDEX IF NOT EXISTS idx_dau_dispute_update_type
    ON dispute_administrative_updates (dispute_id, update_type);

-- ─── 4. cases composite (company_id, status) ──────────────────────────────
CREATE INDEX IF NOT EXISTS idx_cases_company_status
    ON cases (company_id, status)
    WHERE archived_at IS NULL;

-- ─── 5. persons composite (company_id, system_role) ──────────────────────
CREATE INDEX IF NOT EXISTS idx_persons_company_role
    ON persons (company_id, system_role);

-- ─── 6. pg_trgm GIN — fast LIKE '%term%' full-text search ─────────────────
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_disputes_title_trgm
    ON disputes USING GIN (title gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_disputes_opponent_trgm
    ON disputes USING GIN (opponent_name gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_cases_title_trgm
    ON cases USING GIN (title gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_cases_case_number_trgm
    ON cases USING GIN (case_number gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_persons_full_name_trgm
    ON persons USING GIN (full_name gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_persons_company_name_trgm
    ON persons USING GIN (company_name gin_trgm_ops);
