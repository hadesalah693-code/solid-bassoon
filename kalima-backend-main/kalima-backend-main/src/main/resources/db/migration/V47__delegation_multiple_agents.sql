-- Multiple agents per delegation (الوكلاء). Keeps agent_id as the primary agent for calendar/search compat.

CREATE TABLE delegation_agents (
    delegation_id UUID NOT NULL REFERENCES delegations (id) ON DELETE CASCADE,
    person_id UUID NOT NULL REFERENCES persons (id),
    CONSTRAINT pk_delegation_agents PRIMARY KEY (delegation_id, person_id)
);

CREATE INDEX idx_delegation_agents_person ON delegation_agents (person_id);

INSERT INTO delegation_agents (delegation_id, person_id)
SELECT id, agent_id
FROM delegations
WHERE agent_id IS NOT NULL;
