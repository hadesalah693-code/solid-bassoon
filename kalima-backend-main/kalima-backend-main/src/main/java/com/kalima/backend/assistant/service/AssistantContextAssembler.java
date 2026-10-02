package com.kalima.backend.assistant.service;

import com.kalima.backend.domain.entity.Person;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Assembles page-context snippets for the Lawyer Assistant and voice agent.
 */
@Service
@Transactional(readOnly = true)
public class AssistantContextAssembler {

    private final CaseContextBuilder caseContextBuilder;
    private final DelegationContextBuilder delegationContextBuilder;
    private final DisputeContextBuilder disputeContextBuilder;

    public AssistantContextAssembler(CaseContextBuilder caseContextBuilder,
                                     DelegationContextBuilder delegationContextBuilder,
                                     DisputeContextBuilder disputeContextBuilder) {
        this.caseContextBuilder = caseContextBuilder;
        this.delegationContextBuilder = delegationContextBuilder;
        this.disputeContextBuilder = disputeContextBuilder;
    }

    public record AssembledContext(
            String promptBlock,
            boolean contextUsed,
            UUID effectiveCaseId
    ) {
    }

    public AssembledContext assemble(
            UUID caseId,
            UUID delegationId,
            UUID disputeId,
            Person actor) {

        List<String> sections = new ArrayList<>();
        UUID resolvedCaseId = caseId;

        if (delegationId != null) {
            sections.add(delegationContextBuilder.buildContext(delegationId, actor));
            if (resolvedCaseId == null) {
                resolvedCaseId = delegationContextBuilder.resolveLinkedCaseId(delegationId);
            }
        }
        if (disputeId != null) {
            sections.add(disputeContextBuilder.buildContext(disputeId, actor));
            if (resolvedCaseId == null) {
                resolvedCaseId = disputeContextBuilder.resolveConvertedCaseId(disputeId);
            }
        }
        if (caseId != null) {
            sections.add(caseContextBuilder.buildContext(caseId, actor));
            resolvedCaseId = caseId;
        } else if (resolvedCaseId != null && (delegationId != null || disputeId != null)) {
            sections.add(caseContextBuilder.buildContext(resolvedCaseId, actor));
        }

        if (sections.isEmpty()) {
            return new AssembledContext("", false, null);
        }

        StringBuilder block = new StringBuilder();
        block.append("\n\n=== سياق الصفحة الحالية (بيانات حقيقية من النظام) ===\n");
        block.append("استخدم هذه البيانات حصراً. لا تختلق.\n\n");
        block.append(String.join("\n\n", sections));
        block.append("\n\n=== نهاية سياق الصفحة ===\n");

        return new AssembledContext(block.toString(), true, resolvedCaseId);
    }

    /** Snippet for voice agent memory when user opens assistant from a page. */
    public String buildPageSnippet(
            UUID caseId,
            UUID delegationId,
            UUID disputeId,
            Person actor) {
        AssembledContext ctx = assemble(caseId, delegationId, disputeId, actor);
        if (!ctx.contextUsed()) {
            return "";
        }
        return ctx.promptBlock();
    }
}
