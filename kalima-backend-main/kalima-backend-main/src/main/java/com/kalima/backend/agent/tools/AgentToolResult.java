package com.kalima.backend.agent.tools;

import java.util.UUID;

public record AgentToolResult(
        boolean success,
        Object data,
        String message,
        UUID caseIdHint
) {
    public static AgentToolResult ok(Object data, String message) {
        return new AgentToolResult(true, data, message, null);
    }

    public static AgentToolResult ok(Object data, String message, java.util.UUID caseId) {
        return new AgentToolResult(true, data, message, caseId);
    }

    public static AgentToolResult fail(String message) {
        return new AgentToolResult(false, null, message, null);
    }
}
