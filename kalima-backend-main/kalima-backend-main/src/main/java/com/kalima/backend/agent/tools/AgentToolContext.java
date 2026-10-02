package com.kalima.backend.agent.tools;

import com.kalima.backend.domain.entity.Person;

import java.util.Map;
import java.util.UUID;

public record AgentToolContext(
        Person actor,
        UUID companyId,
        boolean nightAgentMode
) {
}
