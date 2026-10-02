package com.kalima.backend.agent.safety;

import com.kalima.backend.agent.config.AgentProperties;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class AgentSafetyService {

    /** Tools that never run without explicit user confirmation. */
    private static final Set<String> CONFIRMATION_REQUIRED = Set.of(
            "close_case",
            "modify_case_sensitive_data",
            "send_notification"
    );

    /** Tools blocked entirely for autonomous (night) execution. */
    private static final Set<String> NIGHT_AGENT_BLOCKED = Set.of(
            "close_case",
            "modify_case_sensitive_data",
            "send_notification"
    );

    private static final Set<String> BLOCKED_TOOLS = Set.of(
            "delete_case",
            "archive_case"
    );

    private final AgentProperties properties;

    public AgentSafetyService(AgentProperties properties) {
        this.properties = properties;
    }

    public boolean requiresConfirmation(String toolName) {
        return CONFIRMATION_REQUIRED.contains(normalize(toolName));
    }

    public boolean isNightAgentAllowed(String toolName) {
        return !NIGHT_AGENT_BLOCKED.contains(normalize(toolName));
    }

    public boolean isToolBlocked(String toolName) {
        return BLOCKED_TOOLS.contains(normalize(toolName));
    }

    public boolean isRegisteredTool(String toolName, java.util.Set<String> registeredTools) {
        return registeredTools.contains(normalize(toolName));
    }

    public boolean meetsConfidenceThreshold(double confidence) {
        return confidence >= properties.getConfidenceThreshold();
    }

    public double confidenceThreshold() {
        return properties.getConfidenceThreshold();
    }

    private static String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase();
    }
}
