-- AI Agent layer: memory, pending confirmations, command log, night runs.

CREATE TABLE agent_memory (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id      UUID         NOT NULL REFERENCES companies(id),
    person_id       UUID         NOT NULL REFERENCES persons(id),
    memory_key      VARCHAR(120) NOT NULL,
    memory_value    TEXT         NOT NULL,
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_agent_memory_person_key UNIQUE (company_id, person_id, memory_key)
);

CREATE INDEX idx_agent_memory_person ON agent_memory(company_id, person_id);

CREATE TABLE agent_pending_actions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id      UUID         NOT NULL REFERENCES companies(id),
    person_id       UUID         NOT NULL REFERENCES persons(id),
    tool_name       VARCHAR(80)  NOT NULL,
    tool_params     TEXT         NOT NULL,
    summary         TEXT         NOT NULL,
    confidence      DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    expires_at      TIMESTAMP    NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    executed_at     TIMESTAMP,
    cancelled_at    TIMESTAMP
);

CREATE INDEX idx_agent_pending_person ON agent_pending_actions(company_id, person_id)
    WHERE executed_at IS NULL AND cancelled_at IS NULL;

CREATE TABLE agent_command_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id      UUID         NOT NULL REFERENCES companies(id),
    person_id       UUID         NOT NULL REFERENCES persons(id),
    input_text      TEXT         NOT NULL,
    reply_text      TEXT,
    tools_used      TEXT,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_agent_command_log_person ON agent_command_log(company_id, person_id, created_at DESC);

CREATE TABLE night_agent_runs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id      UUID         NOT NULL REFERENCES companies(id),
    report_json     TEXT         NOT NULL,
    auto_actions    INT          NOT NULL DEFAULT 0,
    ran_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_night_agent_runs_company ON night_agent_runs(company_id, ran_at DESC);
