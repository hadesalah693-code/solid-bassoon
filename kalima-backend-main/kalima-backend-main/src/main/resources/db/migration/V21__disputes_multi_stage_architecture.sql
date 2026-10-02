-- Multi-stage disputes: stages, case links (many cases per dispute), administrative updates.
-- Keeps disputes.converted_case_id for backward compatibility; seeds dispute_case_links from it.

-- ─── dispute_stages ───────────────────────────────────────────────────────
CREATE TABLE dispute_stages (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    dispute_id UUID NOT NULL,
    stage_type VARCHAR(50) NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    title VARCHAR(500),
    started_at TIMESTAMP,
    closed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_ds_company FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_ds_dispute FOREIGN KEY (dispute_id) REFERENCES disputes (id) ON DELETE CASCADE
);

CREATE INDEX idx_ds_company_id ON dispute_stages (company_id);
CREATE INDEX idx_ds_dispute_id ON dispute_stages (dispute_id);
CREATE INDEX idx_ds_company_dispute ON dispute_stages (company_id, dispute_id);
CREATE INDEX idx_ds_stage_type ON dispute_stages (stage_type);
CREATE INDEX idx_ds_status ON dispute_stages (status);
CREATE INDEX idx_ds_created_at ON dispute_stages (created_at);

-- ─── dispute_case_links ───────────────────────────────────────────────────
CREATE TABLE dispute_case_links (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    dispute_id UUID NOT NULL,
    case_id UUID NOT NULL,
    link_role VARCHAR(40) NOT NULL DEFAULT 'LINKED',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_dcl_company FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_dcl_dispute FOREIGN KEY (dispute_id) REFERENCES disputes (id) ON DELETE CASCADE,
    CONSTRAINT fk_dcl_case FOREIGN KEY (case_id) REFERENCES cases (id),
    CONSTRAINT uq_dcl_dispute_case UNIQUE (dispute_id, case_id)
);

CREATE INDEX idx_dcl_company_id ON dispute_case_links (company_id);
CREATE INDEX idx_dcl_dispute_id ON dispute_case_links (dispute_id);
CREATE INDEX idx_dcl_case_id ON dispute_case_links (case_id);
CREATE INDEX idx_dcl_company_dispute ON dispute_case_links (company_id, dispute_id);
CREATE INDEX idx_dcl_created_at ON dispute_case_links (created_at);

-- ─── dispute_administrative_updates ─────────────────────────────────────────
CREATE TABLE dispute_administrative_updates (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    dispute_id UUID NOT NULL,
    stage_id UUID,
    update_type VARCHAR(40) NOT NULL,
    title VARCHAR(500) NOT NULL,
    description TEXT,
    occurred_at TIMESTAMP,
    created_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_dau_company FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_dau_dispute FOREIGN KEY (dispute_id) REFERENCES disputes (id) ON DELETE CASCADE,
    CONSTRAINT fk_dau_stage FOREIGN KEY (stage_id) REFERENCES dispute_stages (id) ON DELETE SET NULL,
    CONSTRAINT fk_dau_created_by FOREIGN KEY (created_by) REFERENCES persons (id)
);

CREATE INDEX idx_dau_company_id ON dispute_administrative_updates (company_id);
CREATE INDEX idx_dau_dispute_id ON dispute_administrative_updates (dispute_id);
CREATE INDEX idx_dau_stage_id ON dispute_administrative_updates (stage_id);
CREATE INDEX idx_dau_company_dispute ON dispute_administrative_updates (company_id, dispute_id);
CREATE INDEX idx_dau_update_type ON dispute_administrative_updates (update_type);
CREATE INDEX idx_dau_created_at ON dispute_administrative_updates (created_at);

-- ─── Backfill: legacy converted_case_id → dispute_case_links ─────────────
INSERT INTO dispute_case_links (id, company_id, dispute_id, case_id, link_role, created_at)
SELECT gen_random_uuid(),
       d.company_id,
       d.id,
       d.converted_case_id,
       'CONVERTED_LEGACY',
       COALESCE(d.updated_at, d.created_at, NOW())
FROM disputes d
WHERE d.converted_case_id IS NOT NULL
  AND NOT EXISTS (
        SELECT 1
        FROM dispute_case_links x
        WHERE x.dispute_id = d.id
          AND x.case_id = d.converted_case_id
    );
