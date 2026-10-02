package com.kalima.backend.agent.tools;

import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

import org.springframework.stereotype.Component;

/**
 * Registry of agent tools. Each tool delegates to {@link AgentToolExecutor}.
 */
@Component
public class AgentToolRegistry {

    private final AgentToolExecutor executor;
    private final Map<String, BiFunction<AgentToolContext, Map<String, Object>, AgentToolResult>> tools;

    public AgentToolRegistry(AgentToolExecutor executor) {
        this.executor = executor;
        this.tools = Map.ofEntries(
                Map.entry("open_case", executor::openCase),
                Map.entry("close_case", executor::closeCase),
                Map.entry("get_case_summary", executor::getCaseSummary),
                Map.entry("get_hearings", executor::getHearings),
                Map.entry("list_cases", executor::listCases),
                Map.entry("list_delegations", executor::listDelegations),
                Map.entry("get_delegation_summary", executor::getDelegationSummary),
                Map.entry("list_expiring_delegations", executor::listExpiringDelegations),
                Map.entry("list_disputes", executor::listDisputes),
                Map.entry("get_dispute_summary", executor::getDisputeSummary),
                Map.entry("create_delegation", executor::createDelegation),
                Map.entry("send_notification", executor::sendNotification),
                Map.entry("assign_task", executor::assignTask),
                Map.entry("generate_preparation_notes", executor::generatePreparationNotes),
                Map.entry("modify_case_sensitive_data", executor::modifyCaseSensitiveData)
        );
    }

    public Set<String> toolNames() {
        return tools.keySet();
    }

    public String toolCatalogForPrompt() {
        return """
                Available tools (JSON params):
                - open_case: {nameOrNumber} — find case by title/number
                - get_case_summary: {caseId}
                - get_hearings: {from?, to?} ISO dates or {date} single day
                - list_cases: {status?, search?, limit?}
                - list_delegations: {search?, limit?}
                - get_delegation_summary: {delegationId}
                - list_expiring_delegations: {withinDays?} default 30
                - list_disputes: {search?, limit?}
                - get_dispute_summary: {disputeId}
                - assign_task: {caseId, title, assignedToId?, dueDate?}
                - create_delegation: {caseId, notes?} — creates prep task + reminder
                - send_notification: {userId, message, caseId?}
                - close_case: {caseId} — REQUIRES CONFIRMATION
                - modify_case_sensitive_data: {caseId, title?, courtName?, assignedLawyerId?} — REQUIRES CONFIRMATION
                - generate_preparation_notes: {caseId} — multi-step read-only summary for hearing prep
                NEVER invent case IDs or user IDs — use tool results only.
                """;
    }

    public AgentToolResult invoke(String toolName, AgentToolContext ctx, Map<String, Object> params) {
        BiFunction<AgentToolContext, Map<String, Object>, AgentToolResult> fn = tools.get(toolName);
        if (fn == null) {
            return AgentToolResult.fail("Unknown tool: " + toolName);
        }
        return fn.apply(ctx, params == null ? Map.of() : params);
    }
}
