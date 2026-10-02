package com.kalima.backend.agent.tools;

import java.util.Map;

/**
 * One planning step from the agent brain (LLM or fallback).
 */
public record AgentStepDecision(
        boolean done,
        double confidence,
        String tool,
        Map<String, Object> params,
        String reply,
        String pendingSummary
) {
    public static AgentStepDecision finished(String reply, double confidence) {
        return new AgentStepDecision(true, confidence, null, Map.of(), reply, null);
    }

    public static AgentStepDecision invokeTool(
            String tool,
            Map<String, Object> params,
            double confidence) {
        return new AgentStepDecision(false, confidence, tool, params, null, null);
    }

    public static AgentStepDecision needsConfirmation(
            String tool,
            Map<String, Object> params,
            String summary,
            double confidence) {
        return new AgentStepDecision(true, confidence, tool, params, null, summary);
    }
}
