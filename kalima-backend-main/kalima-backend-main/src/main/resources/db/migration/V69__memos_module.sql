CREATE TABLE memos (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies (id),
    case_id UUID NOT NULL REFERENCES cases (id) ON DELETE CASCADE,
    title VARCHAR(250) NOT NULL,
    memo_type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    author_id UUID NOT NULL REFERENCES persons (id),
    reviewer_id UUID REFERENCES persons (id),
    content TEXT NOT NULL,
    submitted_at TIMESTAMP,
    approved_at TIMESTAMP,
    sent_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_memos_type CHECK (
        memo_type IN ('DEFENSE_MEMO', 'RESPONSE_MEMO', 'LEGAL_OPINION', 'GENERAL_MEMO')
    ),
    CONSTRAINT chk_memos_status CHECK (
        status IN ('DRAFT', 'UNDER_REVIEW', 'APPROVED', 'SENT', 'ARCHIVED')
    ),
    CONSTRAINT chk_memos_title CHECK (length(trim(title)) > 0),
    CONSTRAINT chk_memos_content CHECK (length(trim(content)) > 0)
);

CREATE INDEX idx_memos_company_case_updated
    ON memos (company_id, case_id, updated_at DESC);
CREATE INDEX idx_memos_company_status_updated
    ON memos (company_id, status, updated_at DESC);